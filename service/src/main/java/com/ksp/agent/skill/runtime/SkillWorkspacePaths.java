package com.ksp.agent.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves per-skill base directories under a materialized workspace
 * ({@code <workspace>/<skillId>/scripts/...}).
 */
public final class SkillWorkspacePaths {

    private SkillWorkspacePaths() {
    }

    /**
     * Prefer a subfolder that looks like a skill root ({@code SKILL.md} or a {@code scripts/}
     * directory); otherwise the sole child directory; otherwise the workspace root.
     */
    public static Path resolveSkillBaseDir(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return workspace;
        }
        List<Path> childDirs = skillChildDirs(workspace);
        if (!childDirs.isEmpty()) {
            return childDirs.getFirst();
        }
        return workspace;
    }

    /**
     * Every directory a skill script might read tool-output from: the workspace root plus each skill
     * subfolder ({@code SKILL.md} or {@code scripts/}). Tool-output mirroring writes to all of them so
     * a script is unaffected by which skill folder it runs from — critical once an assistant has more
     * than one skill, where a single "base dir" guess is ambiguous and non-deterministic.
     */
    public static List<Path> resolveAllSkillBaseDirs(Path workspace) {
        List<Path> dirs = new ArrayList<>();
        if (workspace == null || !Files.isDirectory(workspace)) {
            if (workspace != null) {
                dirs.add(workspace);
            }
            return dirs;
        }
        dirs.add(workspace);
        dirs.addAll(skillChildDirs(workspace));
        return dirs;
    }

    /** Immediate child dirs that look like a skill root, sorted for deterministic ordering. */
    private static List<Path> skillChildDirs(Path workspace) {
        List<Path> result = new ArrayList<>();
        try (var stream = Files.list(workspace)) {
            stream.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve("SKILL.md"))
                            || Files.isDirectory(d.resolve("scripts")))
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            return result;
        }
        return result;
    }
}
