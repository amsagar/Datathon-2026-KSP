package com.ksp.agent.memory.runtime;

import com.ksp.agent.memory.repo.SemanticFactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Applies exponential decay to semantic-fact importance and prunes faded/superseded facts, so
 * unused long-term memories fade while frequently-recalled ones (reinforced on read) survive.
 *
 * <p>Triggered by Catalyst Cron via {@code POST /api/v1/internal/cron/memory-decay} (see
 * {@code InternalCronController}) rather than an in-process {@code @Scheduled} job — a single
 * external scheduler avoids double-firing across AppSail instances.
 */
@Component
@Slf4j
public class MemoryDecayJob {

    private final SemanticFactRepository repository;
    private final double lambda;
    private final double pruneThreshold;

    public MemoryDecayJob(SemanticFactRepository repository,
                          @Value("${agent.memory.decay.lambda:0.02}") double lambda,
                          @Value("${agent.memory.decay.prune-threshold:0.1}") double pruneThreshold) {
        this.repository = repository;
        this.lambda = lambda;
        this.pruneThreshold = pruneThreshold;
    }

    /** Decay then prune. Invoked by Catalyst Cron (see {@code InternalCronController}). */
    public void runDecay() {
        try {
            int decayed = repository.decay(lambda, Instant.now().getEpochSecond());
            int pruned = repository.prune(pruneThreshold);
            log.info("Semantic memory decay: decayed {} fact(s), pruned {} fact(s)", decayed, pruned);
        } catch (RuntimeException e) {
            log.warn("Semantic memory decay run failed: {}", e.getMessage());
        }
    }
}
