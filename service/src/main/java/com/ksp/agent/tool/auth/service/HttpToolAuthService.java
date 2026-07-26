package com.ksp.agent.tool.auth.service;

import tools.jackson.databind.ObjectMapper;
import com.ksp.agent.tool.auth.entity.ToolAuthProfile;
import com.ksp.agent.tool.auth.repo.ToolAuthProfileRepository;
import com.ksp.agent.tool.entity.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the auth headers and query params to apply to an outbound tool call.
 * A tool either references a reusable {@link ToolAuthProfile} (decrypted here,
 * with OAuth client-credentials tokens fetched and cached) or carries inline,
 * non-encrypted auth in its own {@code authType}/{@code authConfig}.
 */
@Service
@Slf4j
public class HttpToolAuthService {

    private static final long EXPIRY_BUFFER_SECONDS = 30;

    public record ResolvedAuth(Map<String, String> headers, Map<String, String> queryParams) {
        static ResolvedAuth empty() {
            return new ResolvedAuth(new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    private final ToolAuthProfileRepository profileRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HttpToolAuthService(ToolAuthProfileRepository profileRepository,
                               EncryptionService encryptionService,
                               ObjectMapper objectMapper,
                               @Value("${agent.http-tool.connect-timeout-ms:10000}") long connectTimeoutMs,
                               @Value("${agent.http-tool.read-timeout-ms:30000}") long readTimeoutMs) {
        this.profileRepository = profileRepository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public ResolvedAuth resolve(AgentTool tool) {
        if (tool.getAuthProfileId() != null && !tool.getAuthProfileId().isBlank()) {
            return profileRepository.findById(tool.getAuthProfileId())
                    .map(this::resolveProfile)
                    .orElseGet(() -> {
                        log.warn("Tool {} references missing auth profile {}", tool.getName(), tool.getAuthProfileId());
                        return ResolvedAuth.empty();
                    });
        }
        return resolveInline(tool.getAuthType(), tool.getAuthConfig());
    }

    private ResolvedAuth resolveInline(String authType, String authConfig) {
        ResolvedAuth resolved = ResolvedAuth.empty();
        String type = authType == null ? "none" : authType.trim();
        if ("none".equals(type) || authConfig == null || authConfig.isBlank()) {
            return resolved;
        }
        Map<String, Object> cfg = parse(authConfig);
        applyKeyed(type, resolved, cfg, str(cfg.get("value")), str(cfg.get("token")), str(cfg.get("password")));
        return resolved;
    }

    private ResolvedAuth resolveProfile(ToolAuthProfile profile) {
        ResolvedAuth resolved = ResolvedAuth.empty();
        String type = profile.getAuthType() == null ? "none" : profile.getAuthType().trim();
        if ("none".equals(type)) {
            return resolved;
        }
        Map<String, Object> cfg = parse(profile.getAuthConfig());
        String secret = decrypt(profile.getEncryptedClientSecret());

        if ("oauth_client_credentials".equals(type)) {
            String token = obtainOAuthToken(profile, cfg, secret);
            if (token != null) {
                resolved.headers().put(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            return resolved;
        }
        applyKeyed(type, resolved, cfg, secret, secret, secret);
        return resolved;
    }

    private void applyKeyed(String type, ResolvedAuth resolved, Map<String, Object> cfg,
                            String apiKeyValue, String bearerToken, String basicPassword) {
        switch (type) {
            case "api_key", "api_key_header" -> {
                String name = str(cfg.get("name"));
                if (name != null && apiKeyValue != null) {
                    if ("query".equalsIgnoreCase(str(cfg.get("in")))) {
                        resolved.queryParams().put(name, apiKeyValue);
                    } else {
                        resolved.headers().put(name, apiKeyValue);
                    }
                }
            }
            case "bearer", "bearer_token" -> {
                if (bearerToken != null) {
                    resolved.headers().put(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
                }
            }
            case "basic", "basic_auth" -> {
                String username = str(cfg.get("username"));
                if (username != null) {
                    String creds = username + ":" + (basicPassword == null ? "" : basicPassword);
                    String encoded = Base64.getEncoder()
                            .encodeToString(creds.getBytes(StandardCharsets.UTF_8));
                    resolved.headers().put(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
                }
            }
            default -> log.debug("Unknown auth type '{}'", type);
        }
    }

    private String obtainOAuthToken(ToolAuthProfile profile, Map<String, Object> cfg, String clientSecret) {
        long now = Instant.now().getEpochSecond();
        if (profile.getEncryptedAccessToken() != null
                && profile.getTokenExpiresAt() != null
                && profile.getTokenExpiresAt() > now + EXPIRY_BUFFER_SECONDS) {
            return decrypt(profile.getEncryptedAccessToken());
        }
        String tokenUrl = profile.getTokenUrl();
        String clientId = str(cfg.get("clientId"));
        if (tokenUrl == null || tokenUrl.isBlank() || clientId == null || clientSecret == null) {
            log.warn("OAuth profile {} missing tokenUrl/clientId/clientSecret", profile.getName());
            return null;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            if (profile.getScopes() != null && !profile.getScopes().isBlank()) {
                form.add("scope", profile.getScopes().trim());
            }
            String body = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> node = parse(body == null ? "{}" : body);
            String accessToken = str(node.get("access_token"));
            if (accessToken == null || accessToken.isBlank()) {
                log.warn("OAuth token response for profile {} had no access_token", profile.getName());
                return null;
            }
            long expiresIn = node.get("expires_in") instanceof Number n ? n.longValue() : 3600;
            profileRepository.updateToken(profile.getId(),
                    encryptionService.encrypt(accessToken), now + expiresIn, now);
            return accessToken;
        } catch (Exception e) {
            log.warn("OAuth client-credentials fetch failed for profile {}: {}",
                    profile.getName(), e.getMessage());
            return null;
        }
    }

    private String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return encryptionService.decrypt(value);
        } catch (Exception e) {
            log.warn("Failed to decrypt secret: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }
}
