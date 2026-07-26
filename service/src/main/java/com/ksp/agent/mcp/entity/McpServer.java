package com.ksp.agent.mcp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A remote MCP (Model Context Protocol) server owned by a single assistant. Mirrors the ownership
 * model of {@code AgentTool}: {@code assistantId} is a NOT NULL FK with ON DELETE CASCADE.
 * Secrets ({@code encryptedSecret}, {@code encryptedAccessToken}) are stored encrypted.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class McpServer {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private String transport;            // 'streamable_http' | 'sse'
    private String url;                  // base URL / endpoint
    private String sseEndpoint;          // optional SSE path override
    private String authType;             // none | api_key_header | bearer_token | basic_auth | oauth_client_credentials | oauth_auth_code
    private String authConfig;           // JSON: non-secret fields
    private String encryptedSecret;      // encrypted apiKey/token/password/clientSecret
    private String encryptedAccessToken; // cached OAuth access token (encrypted)
    private Long tokenExpiresAt;
    private boolean enabled;
    private String status;               // 'connected' | 'error' | null
    private String statusDetail;
    private Long createdAt;
    private Long updatedAt;
}
