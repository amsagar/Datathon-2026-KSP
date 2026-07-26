package com.ksp.agent.tool.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentTool {
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
