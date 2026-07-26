package com.ksp.agent.chat.dto.response;

public record UsageDailyRowDto(
        String day,
        long requestCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        double estimatedCostUsd
) {
}
