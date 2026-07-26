package com.ksp.agent.chat.usage;

/**
 * Correlates auxiliary LLM calls (title, scope guard, summarization) with the chat turn that
 * triggered them via a shared {@code requestId}. Carries the acting {@code userId} captured on the
 * request thread, because usage is recorded on reactive/async threads where the SecurityContext is
 * not available.
 */
public record LlmUsageContext(String requestId, String sessionId, String assistantId, String userId) {
}
