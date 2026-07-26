package com.ksp.agent.skill.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlatformSkillDto {
    private String id;
    private String name;
    private String description;
    /** Skill ships with default: true — active unless the assistant explicitly disables it. */
    private boolean defaultEnabled;
    /** Resolved per the requested assistant (or defaultEnabled when no assistant given). */
    private boolean active;
}
