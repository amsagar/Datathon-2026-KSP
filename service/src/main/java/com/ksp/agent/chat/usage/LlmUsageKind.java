package com.ksp.agent.chat.usage;

/** High-level bucket for usage reporting (e.g. By model shows {@link #SYSTEM} as "System"). */
public enum LlmUsageKind {
    chat,
    system
}
