package com.ksp.agent.chat.usage;

import com.ksp.agent.chat.dto.response.UsageBreakdownRowDto;
import com.ksp.agent.chat.dto.response.UsageDailyRowDto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges a display-bucket usage query (e.g. "by source", "by assistant", daily totals) with a
 * parallel per-model split of the same rows, so {@code estimatedCostUsd} can be attached using the
 * real per-model rate even when the displayed bucket key collapses or differs from the model name
 * itself — a bucket like "by source" or "by assistant" can span several models billed at different
 * rates, so cost has to be computed from the real per-model split and then rolled up into the
 * bucket, rather than priced against the (possibly collapsed) display key.
 */
public final class UsageMetrics {

    private UsageMetrics() {
    }

    /** One (bucketKey, modelName) slice of token usage, as returned by the {@code *_BY_MODEL} queries. */
    public record ModelSplitRow(String bucketKey, String modelName, long promptTokens, long completionTokens) {
    }

    /** Sums estimated cost per {@code bucketKey}, across however many models fed into that bucket. */
    public static Map<String, Double> costByBucket(List<ModelSplitRow> splits, UsageCostEstimator estimator) {
        Map<String, Double> costs = new LinkedHashMap<>();
        for (ModelSplitRow row : splits) {
            double cost = estimator.estimate(row.modelName(), row.promptTokens(), row.completionTokens());
            costs.merge(row.bucketKey(), cost, Double::sum);
        }
        return costs;
    }

    /** Sums estimated cost across every row, ignoring the bucket key entirely (for grand totals). */
    public static double totalCost(List<ModelSplitRow> splits, UsageCostEstimator estimator) {
        double total = 0.0;
        for (ModelSplitRow row : splits) {
            total += estimator.estimate(row.modelName(), row.promptTokens(), row.completionTokens());
        }
        return total;
    }

    public static List<UsageBreakdownRowDto> withCost(List<UsageBreakdownRowDto> rows, Map<String, Double> costByKey) {
        return rows.stream()
                .map(r -> new UsageBreakdownRowDto(
                        r.key(), r.requestCount(), r.promptTokens(), r.completionTokens(), r.totalTokens(),
                        costByKey.getOrDefault(r.key(), 0.0)))
                .toList();
    }

    public static List<UsageDailyRowDto> withCostDaily(List<UsageDailyRowDto> rows, Map<String, Double> costByKey) {
        return rows.stream()
                .map(r -> new UsageDailyRowDto(
                        r.day(), r.requestCount(), r.promptTokens(), r.completionTokens(), r.totalTokens(),
                        costByKey.getOrDefault(r.day(), 0.0)))
                .toList();
    }
}
