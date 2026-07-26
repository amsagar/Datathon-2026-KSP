package com.ksp.agent.skill.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentSkill {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private String blobPrefix;
    private boolean enabled;
    private Long createdAt;
    private Long updatedAt;
}
