package com.ksp.agent.skill.platform;

import com.ksp.agent.skill.service.SkillBundleParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the skills bundled with the platform from {@code classpath:skills/<id>/SKILL.md} at
 * startup and serves them from memory. Pattern resolution (not directory listing) is used so the
 * same code works from {@code target/classes} during development and from inside the fat jar.
 *
 * <p>Per-assistant activation semantics live in {@link #activeFor(String)}: a {@code null}
 * {@code assistant.platform_skills} column means "defaults apply" (every skill with
 * {@code default: true} is active); a non-null value is an explicit comma-separated allow-list
 * (empty string = all platform skills disabled).
 */
@Component
@Slf4j
public class PlatformSkillRegistry {

    private static final String MANIFEST = "SKILL.md";

    private final SkillBundleParser parser;
    private final Map<String, PlatformSkill> skills = new LinkedHashMap<>();

    public PlatformSkillRegistry(SkillBundleParser parser) {
        this.parser = parser;
    }

    @PostConstruct
    void load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] manifests;
        try {
            manifests = resolver.getResources("classpath*:skills/*/" + MANIFEST);
        } catch (IOException e) {
            log.warn("Could not scan classpath for platform skills: {}", e.getMessage());
            return;
        }
        for (Resource manifest : manifests) {
            try {
                String id = skillIdOf(manifest);
                if (id == null) {
                    log.warn("Skipping platform skill manifest with unresolvable path: {}", manifest);
                    continue;
                }
                Map<String, byte[]> files = loadFiles(resolver, id);
                byte[] manifestBytes = files.get(MANIFEST);
                if (manifestBytes == null) {
                    log.warn("Platform skill '{}' has no readable SKILL.md, skipping", id);
                    continue;
                }
                SkillBundleParser.SkillFrontmatter fm =
                        parser.readFrontmatter(new String(manifestBytes, StandardCharsets.UTF_8));
                String name = fm.name() == null || fm.name().isBlank() ? id : fm.name();
                skills.put(id, new PlatformSkill(id, name, fm.description(), fm.defaultEnabled(), files));
            } catch (RuntimeException e) {
                log.warn("Failed to load platform skill from {}: {}", manifest, e.getMessage());
            }
        }
        log.info("Loaded {} platform skill(s): {}", skills.size(), String.join(", ", skills.keySet()));
    }

    /** Folder name between {@code skills/} and {@code /SKILL.md} in the resource URL. */
    private String skillIdOf(Resource manifest) throws RuntimeException {
        try {
            String url = manifest.getURL().toString();
            int end = url.lastIndexOf('/' + MANIFEST);
            int start = url.lastIndexOf("skills/", end);
            if (start < 0 || end <= start) {
                return null;
            }
            String id = url.substring(start + "skills/".length(), end);
            return id.contains("/") ? null : id;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, byte[]> loadFiles(PathMatchingResourcePatternResolver resolver, String id) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String base = "skills/" + id + "/";
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath*:" + base + "**");
        } catch (IOException e) {
            log.warn("Could not list files for platform skill '{}': {}", id, e.getMessage());
            return files;
        }
        for (Resource resource : resources) {
            try {
                String url = resource.getURL().toString();
                int idx = url.lastIndexOf(base);
                if (idx < 0) {
                    continue;
                }
                String relative = url.substring(idx + base.length());
                // Skip directory entries (jar listings include them with a trailing slash).
                if (relative.isBlank() || relative.endsWith("/")) {
                    continue;
                }
                files.put(relative, resource.getInputStream().readAllBytes());
            } catch (IOException e) {
                log.warn("Could not read platform skill file {}: {}", resource, e.getMessage());
            }
        }
        return files;
    }

    public List<PlatformSkill> all() {
        return List.copyOf(skills.values());
    }

    public Optional<PlatformSkill> byId(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    /**
     * Resolves the active platform skills for an assistant from its raw {@code platform_skills}
     * column: {@code null} → all {@code default: true} skills; otherwise the explicit
     * comma-separated id list (unknown ids ignored, empty string → none).
     */
    public List<PlatformSkill> activeFor(String platformSkillsColumn) {
        if (platformSkillsColumn == null) {
            return skills.values().stream().filter(PlatformSkill::defaultEnabled).toList();
        }
        Set<String> wanted = Arrays.stream(platformSkillsColumn.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        List<PlatformSkill> active = new ArrayList<>();
        for (PlatformSkill skill : skills.values()) {
            if (wanted.contains(skill.id())) {
                active.add(skill);
            }
        }
        return active;
    }

    /** Active skill ids for an assistant's raw column value — see {@link #activeFor(String)}. */
    public List<String> activeIdsFor(String platformSkillsColumn) {
        return activeFor(platformSkillsColumn).stream().map(PlatformSkill::id).toList();
    }

    /** Drops ids that don't match any loaded platform skill (logged), preserving order. */
    public List<String> sanitizeIds(List<String> ids) {
        List<String> valid = new ArrayList<>();
        for (String id : ids) {
            String trimmed = id == null ? "" : id.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (skills.containsKey(trimmed)) {
                valid.add(trimmed);
            } else {
                log.warn("Ignoring unknown platform skill id '{}'", trimmed);
            }
        }
        return valid;
    }
}
