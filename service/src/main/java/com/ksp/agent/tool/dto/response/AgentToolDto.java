package com.ksp.agent.tool.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentToolDto {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private String method;
    private String host;
    private String endpoint;
    private String requestSchema;
    private String sourceType;
    private String authProfileId;
    private String authType;
    private String authConfig;
    private String groupId;
    private boolean enabled;
    private Long createdAt;
    private Long updatedAt;
}
