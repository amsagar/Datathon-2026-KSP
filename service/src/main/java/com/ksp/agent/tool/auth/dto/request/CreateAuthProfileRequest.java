package com.ksp.agent.tool.auth.dto.request;

import lombok.Data;

@Data
public class CreateAuthProfileRequest {
    private String name;
    private String description;
    private String authType;
    private String authConfig;
    private String clientSecret;
    private String tokenUrl;
    private String scopes;
}
