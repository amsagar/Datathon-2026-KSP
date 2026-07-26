package com.ksp.agent.mcp.dto.request;

import lombok.Data;

@Data
public class CreateMcpServerRequest {
    private String name;
    private String description;
    private String transport;   // 'streamable_http' | 'sse'
    private String url;
    private String sseEndpoint;
    private String authType;     // none | api_key_header | bearer_token | basic_auth | oauth_client_credentials | oauth_auth_code
    private String authConfig;   // JSON: non-secret fields (e.g. {"name":"X-Api-Key"} or {"clientId":..,"tokenUrl":..,"scopes":..})
    private String secret;       // plaintext secret; encrypted server-side
    private Boolean enabled;
}
