package com.ksp.agent.skill.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillDto {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private boolean enabled;
    private Long createdAt;
    private Long updatedAt;
}
