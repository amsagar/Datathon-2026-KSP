package com.ksp.agent.skill.runtime;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps shell/Bash tools so commands run in the materialized skill base directory instead of the
 * JVM working directory (typically the Spring Boot service folder).
 */
public class SkillWorkspaceShellToolCallback implements ToolCallback {

    public static final String SKILL_WORKSPACE = "skillWorkspace";

    private final ToolCallback delegate;
    private final ObjectMapper objectMapper;

    public SkillWorkspaceShellToolCallback(ToolCallback delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        Path skillBase = resolveSkillBase(toolContext);
        if (skillBase != null) {
            toolInput = prependChangeDirectory(toolInput, skillBase);
        }
        return delegate.call(toolInput, toolContext);
    }

    private Path resolveSkillBase(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object workspace = toolContext.getContext().get(SKILL_WORKSPACE);
        if (workspace == null || String.valueOf(workspace).isBlank()) {
            return null;
        }
        return SkillWorkspacePaths.resolveSkillBaseDir(Path.of(String.valueOf(workspace)));
    }

    /**
     * Shell tools accept JSON {@code {"command":"...", ...}} or a raw command string.
     */
    private String prependChangeDirectory(String toolInput, Path skillBase) {
        String cdPrefix = buildCdPrefix(skillBase);
        if (toolInput == null || toolInput.isBlank()) {
            return cdPrefix;
        }
        String trimmed = toolInput.trim();
        if (trimmed.startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(trimmed, Map.class);
                Object command = map.get("command");
                if (command != null) {
                    String rewritten = cdPrefix + command;
                    map.put("command", rewritten);
                    return objectMapper.writeValueAsString(map);
                }
            } catch (Exception ignored) {
                // fall through to raw command
            }
        }
        return cdPrefix + trimmed;
    }

    private static String buildCdPrefix(Path skillBase) {
        String path = skillBase.toAbsolutePath().toString();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String pythonDir = PythonLauncher.interpreterDir();
        if (windows) {
            // Prepend the real interpreter's dir so `python`/`python3` resolve to it instead of the
            // Microsoft Store stub under WindowsApps (which exits 255 and breaks skill scripts).
            String pathFix = pythonDir.isBlank() ? "" : "set \"PATH=" + pythonDir + ";%PATH%\" && ";
            return pathFix + "cd /d \"" + path + "\" && ";
        }
        String pathFix = pythonDir.isBlank() ? "" : "export PATH=\"" + pythonDir + ":$PATH\" && ";
        return pathFix + "cd \"" + path + "\" && ";
    }
}
