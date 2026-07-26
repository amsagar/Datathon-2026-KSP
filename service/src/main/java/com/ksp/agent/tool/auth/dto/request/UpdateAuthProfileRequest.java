package com.ksp.agent.tool.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateAuthProfileRequest {
    private String name;
    private String description;
    private String authType;
    private String authConfig;
    private String clientSecret;
    private String tokenUrl;
    private String scopes;
}
