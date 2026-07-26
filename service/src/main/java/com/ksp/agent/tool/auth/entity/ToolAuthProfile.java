package com.ksp.agent.tool.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolAuthProfile {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private String authType;
    private String authConfig;
    private String encryptedClientSecret;
    private String tokenUrl;
    private String scopes;
    private String encryptedAccessToken;
    private Long tokenExpiresAt;
    private Long createdAt;
    private Long updatedAt;
}
