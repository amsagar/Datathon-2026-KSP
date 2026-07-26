package com.ksp.agent.skill.platform;

import java.util.Map;

/**
 * A skill bundled with the platform itself (under {@code classpath:skills/<id>/}) rather than
 * uploaded by a user. Loaded once at startup; {@code files} maps each relative path (always
 * including {@code SKILL.md}) to its bytes so chat-turn materialization never touches the
 * classpath again.
 *
 * @param id             folder name under {@code resources/skills/} — the stable identifier
 *                       stored in {@code assistant.platform_skills}
 * @param name           display name from SKILL.md frontmatter (falls back to {@code id})
 * @param description    description from SKILL.md frontmatter
 * @param defaultEnabled {@code default: true} in frontmatter — active for every assistant
 *                       that has not customized its platform-skill selection
 * @param files          relativePath → bytes for every file in the skill bundle
 */
public record PlatformSkill(
        String id,
        String name,
        String description,
        boolean defaultEnabled,
        Map<String, byte[]> files) {
}
