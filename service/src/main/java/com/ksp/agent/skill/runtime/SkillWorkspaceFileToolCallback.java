package com.ksp.agent.skill.runtime;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Wraps file-system tools (Read/Write/Edit) so a <em>relative</em> file path resolves against the
 * materialized skill base directory — the same working directory the shell tool runs in.
 *
 * <p>{@code FileSystemTools} resolves relative paths with {@code Paths.get(path)}, i.e. against the
 * JVM working directory (the Spring Boot service folder), while the shell tool {@code cd}s into the
 * skill workspace. Without this alignment a file the model writes (e.g. {@code order_input.json})
 * lands in a different directory than where a {@code Bash} command looks for it, and the skill's own
 * {@code references/*} files cannot be read with a relative path.
 */
public class SkillWorkspaceFileToolCallback implements ToolCallback {

    public static final String SKILL_WORKSPACE = SkillWorkspaceShellToolCallback.SKILL_WORKSPACE;

    // FileSystemTools names the parameter "filePath"; accept common variants defensively.
    private static final List<String> PATH_KEYS = List.of("filePath", "file_path", "path");

    private final ToolCallback delegate;
    private final ObjectMapper objectMapper;

    public SkillWorkspaceFileToolCallback(ToolCallback delegate, ObjectMapper objectMapper) {
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
            toolInput = rewriteRelativePath(toolInput, skillBase);
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

    private String rewriteRelativePath(String toolInput, Path skillBase) {
        if (toolInput == null || toolInput.isBlank()) {
            return toolInput;
        }
        String trimmed = toolInput.trim();
        if (!trimmed.startsWith("{")) {
            return toolInput;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(trimmed, Map.class);
            for (String key : PATH_KEYS) {
                Object value = map.get(key);
                if (value instanceof String raw && !raw.isBlank()) {
                    Path p = Path.of(raw);
                    if (!p.isAbsolute()) {
                        map.put(key, skillBase.resolve(raw).normalize().toAbsolutePath().toString());
                        return objectMapper.writeValueAsString(map);
                    }
                    return toolInput;
                }
            }
        } catch (Exception ignored) {
            // Malformed/quoted path or non-JSON payload: leave the input untouched.
        }
        return toolInput;
    }
}
