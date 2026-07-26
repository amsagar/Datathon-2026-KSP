package com.ksp.agent.chat.service.impl;

import com.ksp.agent.chat.dto.response.UsageBreakdownRowDto;
import com.ksp.agent.chat.dto.response.UsageDailyRowDto;
import com.ksp.agent.chat.dto.response.UsageSummaryResponse;
import com.ksp.agent.chat.dto.response.UsageTotalsDto;
import com.ksp.agent.chat.repo.LlmUsageRepository;
import com.ksp.agent.chat.repo.LlmUsageRepository.ModelCostRow;
import com.ksp.agent.chat.service.LlmUsageService;
import com.ksp.agent.chat.usage.LlmUsageKind;
import com.ksp.agent.chat.usage.UsageCostEstimator;
import com.ksp.agent.chat.usage.UsageMetrics;
import com.ksp.agent.chat.usage.UsageMetrics.ModelSplitRow;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmUsageServiceImpl implements LlmUsageService {

    private final LlmUsageRepository repository;
    private final UsageCostEstimator costEstimator;

    public LlmUsageServiceImpl(LlmUsageRepository repository, UsageCostEstimator costEstimator) {
        this.repository = repository;
        this.costEstimator = costEstimator;
    }

    @Override
    public void record(String requestId, String sessionId, String userId, String assistantId,
                       LlmUsageKind usageKind, String usageSource, String modelName,
                       int promptTokens, int completionTokens, int totalTokens) {
        if (totalTokens <= 0 && promptTokens <= 0 && completionTokens <= 0) {
            return;
        }
        int total = totalTokens > 0 ? totalTokens : promptTokens + completionTokens;
        repository.insert(requestId, sessionId, userId, assistantId,
                usageKind.name(), usageSource, modelName,
                promptTokens, completionTokens, total, Instant.now());
    }

    @Override
    public UsageSummaryResponse summary(Instant from, Instant to, String scopedUserId) {
        UsageTotalsDto totals = withCost(repository.summary(from, to, scopedUserId),
                repository.byModelForCost(from, to, scopedUserId));
        Map<String, Double> costByDay = UsageMetrics.costByBucket(
                repository.dailyByModel(from, to, scopedUserId), costEstimator);
        List<UsageDailyRowDto> daily = UsageMetrics.withCostDaily(
                repository.daily(from, to, scopedUserId), costByDay);

        // Prior period of equal length immediately preceding `from`, so the frontend can show
        // %Δ KPIs without a second round trip.
        Instant prevFrom = from.minus(Duration.between(from, to));
        Instant prevTo = from;
        UsageTotalsDto previousTotals = withCost(repository.summary(prevFrom, prevTo, scopedUserId),
                repository.byModelForCost(prevFrom, prevTo, scopedUserId));

        return new UsageSummaryResponse(totals, daily, previousTotals);
    }

    @Override
    public List<UsageBreakdownRowDto> byModel(Instant from, Instant to, String scopedUserId) {
        List<UsageBreakdownRowDto> display = repository.byModel(from, to, scopedUserId);
        List<ModelCostRow> costRows = repository.byModelForCost(from, to, scopedUserId);
        Map<String, Double> costByDisplayKey = new LinkedHashMap<>();
        for (ModelCostRow row : costRows) {
            // Mirror USAGE.BY_MODEL's own display collapse (system-kind rows -> "System") so the
            // real per-model cost lands on the same bucket the display query produced.
            String displayKey = "system".equals(row.usageKind()) ? "System" : row.modelName();
            double cost = costEstimator.estimate(row.modelName(), row.promptTokens(), row.completionTokens());
            costByDisplayKey.merge(displayKey, cost, Double::sum);
        }
        return UsageMetrics.withCost(display, costByDisplayKey);
    }

    @Override
    public List<UsageBreakdownRowDto> byUser(Instant from, Instant to, String scopedUserId) {
        if (scopedUserId != null) {
            UsageTotalsDto t = withCost(repository.summary(from, to, scopedUserId),
                    repository.byModelForCost(from, to, scopedUserId));
            return List.of(new UsageBreakdownRowDto(
                    scopedUserId,
                    t.requestCount(),
                    t.promptTokens(),
                    t.completionTokens(),
                    t.totalTokens(),
                    t.estimatedCostUsd()));
        }
        List<UsageBreakdownRowDto> display = repository.byUser(from, to);
        Map<String, Double> costByUser = UsageMetrics.costByBucket(repository.byUserByModel(from, to), costEstimator);
        return UsageMetrics.withCost(display, costByUser);
    }

    @Override
    public List<UsageBreakdownRowDto> byAssistant(Instant from, Instant to, String scopedUserId) {
        List<UsageBreakdownRowDto> display = repository.byAssistant(from, to, scopedUserId);
        Map<String, Double> costByAssistant = UsageMetrics.costByBucket(
                repository.byAssistantByModel(from, to, scopedUserId), costEstimator);
        return UsageMetrics.withCost(display, costByAssistant);
    }

    @Override
    public List<UsageBreakdownRowDto> bySource(Instant from, Instant to, String scopedUserId) {
        List<UsageBreakdownRowDto> display = repository.bySource(from, to, scopedUserId);
        Map<String, Double> costBySource = UsageMetrics.costByBucket(
                repository.bySourceByModel(from, to, scopedUserId), costEstimator);
        return UsageMetrics.withCost(display, costBySource);
    }

    @Override
    public List<UsageBreakdownRowDto> hourly(Instant from, Instant to, String scopedUserId) {
        List<UsageBreakdownRowDto> display = repository.hourly(from, to, scopedUserId);
        List<ModelSplitRow> splits = repository.hourlyByModel(from, to, scopedUserId);
        Map<String, Double> costByHour = UsageMetrics.costByBucket(splits, costEstimator);

        Map<String, UsageBreakdownRowDto> byHourKey = new LinkedHashMap<>();
        for (UsageBreakdownRowDto row : display) {
            byHourKey.put(row.key(), row);
        }

        // SQL's GROUP BY only emits hours that actually had events; backfill the rest with zeros
        // so the frontend always renders all 24 bars.
        List<UsageBreakdownRowDto> result = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            String key = String.valueOf(hour);
            UsageBreakdownRowDto row = byHourKey.get(key);
            double cost = costByHour.getOrDefault(key, 0.0);
            if (row != null) {
                result.add(new UsageBreakdownRowDto(
                        key, row.requestCount(), row.promptTokens(), row.completionTokens(), row.totalTokens(), cost));
            } else {
                result.add(new UsageBreakdownRowDto(key, 0, 0, 0, 0, 0.0));
            }
        }
        return result;
    }

    private UsageTotalsDto withCost(UsageTotalsDto totals, List<ModelCostRow> costRows) {
        double cost = 0.0;
        for (ModelCostRow row : costRows) {
            cost += costEstimator.estimate(row.modelName(), row.promptTokens(), row.completionTokens());
        }
        return new UsageTotalsDto(
                totals.requestCount(), totals.promptTokens(), totals.completionTokens(), totals.totalTokens(), cost);
    }
}
