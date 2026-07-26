package com.ksp.agent.mcp.dto.request;

import lombok.Data;

/**
 * Partial update. Null fields are left unchanged. {@code secret} is special: null keeps the existing
 * secret; a non-null value replaces it (blank clears it).
 */
@Data
public class UpdateMcpServerRequest {
    private String name;
    private String description;
    private String transport;
    private String url;
    private String sseEndpoint;
    private String authType;
    private String authConfig;
    private String secret;
    private Boolean enabled;
}
