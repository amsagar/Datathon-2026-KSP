package com.ksp.agent.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single, in-house LLM configuration. There is no user-facing provider registry any more:
 * every chat/title/summary/scope-guard/fact-extraction call runs on this one model, configured
 * here (and via environment variables) rather than per-user in the UI.
 *
 * <p>The model is reached over an OpenAI-compatible HTTP endpoint, which lets us target Catalyst
 * QuickML LLM Serving (or any in-house OpenAI-compatible gateway) while keeping Spring AI's full
 * tool-calling and streaming support.
 *
 * <p>Two auth modes:
 * <ul>
 *   <li><b>Static key</b> ({@code apiKey} only) — sent as {@code Authorization: Bearer <apiKey>}.
 *       Fine for a plain OpenAI-compatible gateway.</li>
 *   <li><b>Zoho OAuth refresh</b> ({@code clientId} + {@code clientSecret} + {@code refreshToken}
 *       all set) — required for QuickML LLM Serving, whose access tokens expire hourly. When these
 *       three are present, {@link QuickMlTokenService} mints and caches short-lived access tokens
 *       from the (non-expiring) refresh token and every request is sent as
 *       {@code Authorization: Zoho-oauthtoken <access-token>} plus a {@code CATALYST-ORG} header —
 *       {@code apiKey} is then ignored.</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "agent.llm")
public class LlmProperties {

    /** OpenAI-compatible base URL — HOST ONLY (e.g. {@code https://api.catalyst.zoho.in}), no path. */
    private String baseUrl;

    /**
     * Path appended to {@code baseUrl} for chat calls, e.g. QuickML's full endpoint path
     * {@code /quickml/v1/project/<id>/<model>/chat}. Left blank, Spring AI's own OpenAI default
     * ({@code /v1/chat/completions}) applies — correct for a real OpenAI-compatible gateway.
     */
    private String completionsPath;

    /** Static bearer token / API key. Ignored when the Zoho OAuth refresh fields (below) are set. */
    private String apiKey;

    /** Model identifier the endpoint expects (e.g. a QuickML-served model name). */
    private String model = "unknown";

    /** Optional sampling temperature. */
    private Double temperature;

    /** Optional output token cap. */
    private Integer maxTokens;

    /** Catalyst org id, sent as the {@code CATALYST-ORG} header required by QuickML LLM Serving. */
    private String catalystOrg;

    /** Zoho API console Self Client "Client ID", for the OAuth refresh-token grant. */
    private String clientId;

    /** Zoho API console Self Client "Client Secret", for the OAuth refresh-token grant. */
    private String clientSecret;

    /** Long-lived Zoho OAuth refresh token (does not expire unless revoked). */
    private String refreshToken;

    /** Zoho accounts host for the token endpoint, varies by data center (.com/.in/.eu/...). */
    private String accountsBaseUrl = "https://accounts.zoho.in";
}
