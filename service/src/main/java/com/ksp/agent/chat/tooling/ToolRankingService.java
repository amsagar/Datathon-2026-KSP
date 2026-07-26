package com.ksp.agent.chat.tooling;

import com.ksp.agent.tool.repo.AgentToolRepository;
import com.ksp.agent.tool.runtime.ToolNames;
import com.ksp.agent.tool.service.ToolEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks an assistant's tools against a natural-language query so {@code search_tools} can return
 * only the most relevant ones.
 *
 * <p>Strategy, in order of preference:
 * <ol>
 *   <li><b>pgvector (DB-side):</b> embed the query once, then ask Postgres for cosine similarity of
 *       each of the assistant's enabled, embedded tools via the {@code <=>} operator. This is the
 *       primary path for HTTP tools whose embeddings were precomputed asynchronously.</li>
 *   <li><b>Keyword overlap:</b> fallback for tools not present in the DB scores (e.g. builtin tools
 *       which have no {@code agent_tool} row, or tools whose embedding hasn't been generated yet),
 *       and the sole strategy when no {@link EmbeddingModel} is configured.</li>
 * </ol>
 * Tools are sorted by DB similarity when available, then by keyword score, so embedded HTTP tools
 * rank ahead of un-embedded ones for relevant queries while everything still gets ordered sensibly.
 */
@Service
public class ToolRankingService {

    private static final Logger log = LoggerFactory.getLogger(ToolRankingService.class);

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AgentToolRepository agentToolRepository;

    public ToolRankingService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                              AgentToolRepository agentToolRepository) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.agentToolRepository = agentToolRepository;
    }

    public List<ToolCallback> rank(String query, String assistantId,
                                   Collection<ToolCallback> tools, int maxResults) {
        List<ToolCallback> list = new ArrayList<>(tools);
        if (list.size() <= maxResults || query == null || query.isBlank()) {
            return list.size() <= maxResults ? list : keywordRank(query, list, maxResults);
        }

        Map<String, Double> dbScores = similarityScores(query, assistantId);
        String[] terms = query.toLowerCase().split("\\W+");

        return list.stream()
                .sorted(Comparator
                        .comparingDouble((ToolCallback t) -> -dbScore(dbScores, t))
                        .thenComparingInt(t -> -keywordScore(terms, t)))
                .limit(maxResults)
                .toList();
    }

    /** Embed the query and fetch DB-side cosine similarity keyed by sanitized tool name; empty on failure. */
    private Map<String, Double> similarityScores(String query, String assistantId) {
        if (assistantId == null || assistantId.isBlank()) {
            return Map.of();
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return Map.of();
        }
        try {
            float[] q = model.embed(query);
            Map<String, Double> raw = agentToolRepository.similarityScores(
                    assistantId, ToolEmbeddingService.toLiteral(q));
            // DB returns raw agent_tool.name; catalog keys are sanitized model-facing names.
            Map<String, Double> bySanitized = new java.util.HashMap<>();
            for (Map.Entry<String, Double> e : raw.entrySet()) {
                bySanitized.merge(ToolNames.sanitize(e.getKey()), e.getValue(), Math::max);
            }
            return bySanitized;
        } catch (Exception e) {
            log.warn("pgvector tool ranking failed, falling back to keyword: {}", e.getMessage());
            return Map.of();
        }
    }

    private double dbScore(Map<String, Double> dbScores, ToolCallback tool) {
        Double s = dbScores.get(tool.getToolDefinition().name());
        return s == null ? 0.0 : s;
    }

    private List<ToolCallback> keywordRank(String query, List<ToolCallback> tools, int maxResults) {
        String[] terms = query == null ? new String[0] : query.toLowerCase().split("\\W+");
        return tools.stream()
                .sorted(Comparator.comparingInt((ToolCallback t) -> -keywordScore(terms, t)))
                .limit(maxResults)
                .toList();
    }

    private int keywordScore(String[] terms, ToolCallback tool) {
        String haystack = toolText(tool).toLowerCase();
        int score = 0;
        for (String term : terms) {
            if (!term.isBlank() && haystack.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private String toolText(ToolCallback tool) {
        String name = tool.getToolDefinition().name();
        String description = tool.getToolDefinition().description();
        return name + "\n" + (description == null ? "" : description);
    }
}
