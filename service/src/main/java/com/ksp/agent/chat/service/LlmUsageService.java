package com.ksp.agent.chat.service;

import com.ksp.agent.chat.dto.response.UsageBreakdownRowDto;
import com.ksp.agent.chat.dto.response.UsageSummaryResponse;
import com.ksp.agent.chat.usage.LlmUsageKind;

import java.time.Instant;
import java.util.List;

public interface LlmUsageService {

    void record(String requestId, String sessionId, String userId, String assistantId,
                LlmUsageKind usageKind, String usageSource, String modelName,
                int promptTokens, int completionTokens, int totalTokens);

    UsageSummaryResponse summary(Instant from, Instant to, String scopedUserId);

    List<UsageBreakdownRowDto> byModel(Instant from, Instant to, String scopedUserId);

    List<UsageBreakdownRowDto> byUser(Instant from, Instant to, String scopedUserId);

    List<UsageBreakdownRowDto> byAssistant(Instant from, Instant to, String scopedUserId);

    List<UsageBreakdownRowDto> bySource(Instant from, Instant to, String scopedUserId);

    /** Always returns exactly 24 rows (hours 0-23), zero-filled for hours with no usage. */
    List<UsageBreakdownRowDto> hourly(Instant from, Instant to, String scopedUserId);
}
