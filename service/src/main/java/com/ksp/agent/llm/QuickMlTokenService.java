package com.ksp.agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * Mints and caches short-lived Zoho OAuth access tokens from a long-lived refresh token, so QuickML
 * LLM Serving's hourly token expiry never disrupts chat. Mirrors the caching pattern already used
 * for MCP OAuth client-credentials in {@code McpAuthService}, but in memory (no DB row here — this
 * is one global, app-wide credential, not per-server).
 *
 * <p>One-time setup: generate a Self Client in the Zoho API console (api-console.zoho.com), exchange
 * its authorization code for tokens once, and keep only the {@code refresh_token} from that response
 * in config — this service takes it from there.
 */
@Component
@Slf4j
public class QuickMlTokenService {

    /** Refresh this many seconds before actual expiry, to absorb request latency / clock skew. */
    private static final long EXPIRY_BUFFER_SECONDS = 60;

    private final LlmProperties props;
    private final RestClient restClient = RestClient.create();

    private volatile String cachedAccessToken;
    private volatile long expiresAtEpochSecond;

    public QuickMlTokenService(LlmProperties props) {
        this.props = props;
    }

    /** True when the Zoho OAuth refresh-token grant is configured (QuickML LLM Serving mode). */
    public boolean usesOAuthRefresh() {
        return notBlank(props.getClientId()) && notBlank(props.getClientSecret())
                && notBlank(props.getRefreshToken());
    }

    /** A valid access token, refreshing via the refresh-token grant when the cached one is stale. */
    public synchronized String getAccessToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedAccessToken != null && expiresAtEpochSecond > now + EXPIRY_BUFFER_SECONDS) {
            return cachedAccessToken;
        }
        return refresh(now);
    }

    @SuppressWarnings("unchecked")
    private String refresh(long now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", props.getRefreshToken());
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        Map<String, Object> response = restClient.post()
                .uri(props.getAccountsBaseUrl() + "/oauth/v2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        Object accessToken = response == null ? null : response.get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException(
                    "Zoho OAuth refresh returned no access_token (check client-id/client-secret/"
                            + "refresh-token/accounts-base-url): " + response);
        }
        long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 3600;
        cachedAccessToken = String.valueOf(accessToken);
        expiresAtEpochSecond = now + expiresIn;
        log.info("Refreshed QuickML/Zoho OAuth access token (expires in {}s)", expiresIn);
        return cachedAccessToken;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
