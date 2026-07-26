package com.ksp.agent.chat.dto.response;

public record UsageBreakdownRowDto(
        String key,
        long requestCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        double estimatedCostUsd
) {
}
