package com.ksp.agent.audit.config;

import java.util.Locale;

public enum ResourceType {
    assistant, skill, tool, tool_group, tool_auth, document,
    response_style, mcp_server, mcp_tool;

    /** Versioned resources get full {@code config_revision} snapshots + revert; the rest only get feed events. */
    public boolean isVersioned() {
        return this == assistant || this == skill || this == response_style;
    }

    public static ResourceType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Resource type is required");
        }
        try {
            return ResourceType.valueOf(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown resource type: " + value);
        }
    }
}
