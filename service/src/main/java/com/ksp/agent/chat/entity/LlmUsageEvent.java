package com.ksp.agent.chat.entity;

import java.time.Instant;

public record LlmUsageEvent(
        long id,
        String requestId,
        String sessionId,
        String userId,
        String assistantId,
        String modelName,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        Instant createdAt
) {
}
