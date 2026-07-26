package com.ksp.agent.mcp.auth;

import com.ksp.agent.mcp.entity.McpServer;
import com.ksp.agent.mcp.repo.McpServerRepository;
import com.ksp.agent.tool.auth.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the HTTP headers to attach to every request made to an MCP server. Mirrors
 * {@code HttpToolAuthService} but operates on the server's own columns (secrets live on the
 * {@code mcp_server} row, encrypted via {@link EncryptionService}). OAuth client-credentials tokens
 * are fetched and cached on the row, with a small expiry buffer.
 */
@Service
@Slf4j
public class McpAuthService {

    private static final long EXPIRY_BUFFER_SECONDS = 30;

    private final McpServerRepository serverRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public McpAuthService(McpServerRepository serverRepository,
                          EncryptionService encryptionService,
                          ObjectMapper objectMapper) {
        this.serverRepository = serverRepository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    /** Headers to apply to every outbound MCP HTTP request for this server. */
    public Map<String, String> authHeaders(McpServer server) {
        Map<String, String> headers = new LinkedHashMap<>();
        String type = server.getAuthType() == null ? "none" : server.getAuthType().trim();
        if ("none".equals(type)) {
            return headers;
        }
        Map<String, Object> cfg = parse(server.getAuthConfig());
        String secret = decrypt(server.getEncryptedSecret());

        switch (type) {
            case "api_key_header" -> {
                String name = str(cfg.get("name"));
                if (name != null && secret != null) {
                    headers.put(name, secret);
                }
            }
            case "bearer_token" -> {
                if (secret != null) {
                    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + secret);
                }
            }
            case "basic_auth" -> {
                String username = str(cfg.get("username"));
                if (username != null) {
                    String creds = username + ":" + (secret == null ? "" : secret);
                    String encoded = Base64.getEncoder()
                            .encodeToString(creds.getBytes(StandardCharsets.UTF_8));
                    headers.put(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
                }
            }
            case "oauth_client_credentials" -> {
                String token = obtainOAuthToken(server, cfg, secret);
                if (token != null) {
                    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                }
            }
            case "oauth_auth_code" -> {
                // Interactive authorization-code flow is out of scope for v1; if an access token was
                // previously stored on the row, reuse it.
                String token = decrypt(server.getEncryptedAccessToken());
                if (token != null) {
                    headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                }
            }
            default -> log.debug("Unknown MCP auth type '{}'", type);
        }
        return headers;
    }

    private String obtainOAuthToken(McpServer server, Map<String, Object> cfg, String clientSecret) {
        long now = Instant.now().getEpochSecond();
        if (server.getEncryptedAccessToken() != null
                && server.getTokenExpiresAt() != null
                && server.getTokenExpiresAt() > now + EXPIRY_BUFFER_SECONDS) {
            return decrypt(server.getEncryptedAccessToken());
        }
        String tokenUrl = str(cfg.get("tokenUrl"));
        String clientId = str(cfg.get("clientId"));
        String scopes = str(cfg.get("scopes"));
        if (tokenUrl == null || clientId == null || clientSecret == null) {
            log.warn("MCP server {} oauth_client_credentials missing tokenUrl/clientId/clientSecret", server.getName());
            return null;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            if (scopes != null) {
                form.add("scope", scopes);
            }
            String body = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> node = parse(body == null ? "{}" : body);
            String accessToken = str(node.get("access_token"));
            if (accessToken == null) {
                log.warn("MCP server {} OAuth token response had no access_token", server.getName());
                return null;
            }
            long expiresIn = node.get("expires_in") instanceof Number n ? n.longValue() : 3600;
            serverRepository.updateToken(server.getId(),
                    encryptionService.encrypt(accessToken), now + expiresIn, now);
            return accessToken;
        } catch (Exception e) {
            log.warn("MCP server {} OAuth client-credentials fetch failed: {}", server.getName(), e.getMessage());
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
            log.warn("Failed to decrypt MCP secret: {}", e.getMessage());
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
