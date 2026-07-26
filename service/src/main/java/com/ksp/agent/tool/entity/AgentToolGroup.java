package com.ksp.agent.tool.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentToolGroup {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private String sourceType;
    private boolean enabled;
    private Long createdAt;
    private Long updatedAt;
}
