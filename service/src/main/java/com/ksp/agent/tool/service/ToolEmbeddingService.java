package com.ksp.agent.tool.service;

import com.ksp.agent.tool.repo.AgentToolRepository;
import com.ksp.agent.tool.repo.AgentToolRepository.EmbeddingCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generates and persists per-tool embeddings in the {@code agent_tool.embedding} pgvector column.
 * Embeddings power {@code search_tools} (DB-side cosine similarity) so an assistant with many tools
 * only sends the most relevant ones to the model.
 *
 * <p>Work runs asynchronously so a tool create/update never blocks on the embedding API. A hash of
 * the embedded text (name + description) is stored alongside the vector, so re-embedding is skipped
 * when nothing relevant changed. On startup, tools with no embedding are backfilled.
 */
@Service
public class ToolEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ToolEmbeddingService.class);

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AgentToolRepository repository;

    public ToolEmbeddingService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                AgentToolRepository repository) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.repository = repository;
    }

    /** Embed (or re-embed) a single tool by id; no-op if its text is unchanged. */
    @Async
    public void embedToolAsync(String toolId) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            log.debug("No EmbeddingModel configured; skipping embedding for tool {}", toolId);
            return;
        }
        try {
            var tool = repository.findById(toolId).orElse(null);
            if (tool == null) {
                return;
            }
            embedAndStore(model, toolId, tool.getName(), tool.getDescription());
        } catch (Exception e) {
            log.warn("Failed to embed tool {}: {}", toolId, e.getMessage());
        }
    }

    /** Backfill embeddings for any tools missing one (e.g. created before this feature existed). */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return;
        }
        List<EmbeddingCandidate> missing;
        try {
            missing = repository.findMissingEmbedding();
        } catch (Exception e) {
            log.warn("Tool embedding backfill skipped (query failed): {}", e.getMessage());
            return;
        }
        if (missing.isEmpty()) {
            return;
        }
        log.info("Backfilling embeddings for {} tool(s)", missing.size());
        for (EmbeddingCandidate c : missing) {
            try {
                embedAndStore(model, c.id(), c.name(), c.description());
            } catch (Exception e) {
                log.warn("Backfill embedding failed for tool {}: {}", c.id(), e.getMessage());
            }
        }
    }

    private void embedAndStore(EmbeddingModel model, String id, String name, String description) {
        String text = text(name, description);
        String hash = hash(text);
        String existing = repository.findEmbeddingHash(id);
        if (hash.equals(existing)) {
            return;
        }
        float[] vector = model.embed(text);
        repository.updateEmbedding(id, toLiteral(vector), hash);
        log.debug("Embedded tool {} ({} dims)", id, vector.length);
    }

    static String text(String name, String description) {
        return (name == null ? "" : name) + "\n" + (description == null ? "" : description);
    }

    public static String hash(String text) {
        return Integer.toHexString(text.hashCode());
    }

    public static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
