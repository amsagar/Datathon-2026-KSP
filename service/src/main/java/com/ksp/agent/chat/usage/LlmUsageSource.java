package com.ksp.agent.chat.usage;

/** Which LLM call produced the event (stored for audit; rolled into turn totals via {@code request_id}). */
public enum LlmUsageSource {
    main,
    title,
    summary,
    scope_guard,
    consolidation
}
