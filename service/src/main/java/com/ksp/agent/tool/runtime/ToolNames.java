package com.ksp.agent.tool.runtime;

/**
 * Shared tool-name sanitization. The model-facing tool name must match the
 * OpenAI/Azure function-name constraints (alphanumeric, underscore, dash, max 64
 * chars), so a raw {@code agent_tool.name} is normalized here. Centralized so that
 * any code mapping raw DB names back to model-facing catalog keys (e.g. pgvector
 * similarity results) uses the exact same transform.
 */
public final class ToolNames {

    private ToolNames() {
    }

    public static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "tool";
        }
        String cleaned = name.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
