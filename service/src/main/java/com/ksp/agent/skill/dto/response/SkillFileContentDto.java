package com.ksp.agent.skill.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillFileContentDto {
    private String path;
    private String content;
}
