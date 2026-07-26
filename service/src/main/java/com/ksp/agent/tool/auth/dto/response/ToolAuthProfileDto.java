package com.ksp.agent.tool.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolAuthProfileDto {
    private String id;
    private String name;
    private String description;
    private String authType;
    private String authConfig;
    private String tokenUrl;
    private String scopes;
    private boolean hasClientSecret;
    private boolean hasAccessToken;
    private Long tokenExpiresAt;
    private Long createdAt;
    private Long updatedAt;
}
