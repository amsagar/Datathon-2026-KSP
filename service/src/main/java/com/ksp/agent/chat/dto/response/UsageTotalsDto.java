package com.ksp.agent.chat.dto.response;

public record UsageTotalsDto(
        long requestCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        double estimatedCostUsd
) {
}
