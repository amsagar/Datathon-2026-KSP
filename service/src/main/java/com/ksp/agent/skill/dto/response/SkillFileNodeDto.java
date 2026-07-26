package com.ksp.agent.skill.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SkillFileNodeDto {
    /** Relative path from skill root (files only). Empty for folder nodes. */
    private String path;
    private String name;
    /** {@code file} or {@code folder} */
    private String type;
    private List<SkillFileNodeDto> children;
}
