package com.ksp.agent.memory.service;

import com.ksp.agent.memory.dto.response.SemanticFactDto;
import com.ksp.agent.memory.entity.SemanticFact;
import com.ksp.agent.memory.repo.SemanticFactRepository;
import com.ksp.agent.memory.repo.SemanticFactRepository.ScoredFact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Write/read API for the long-term semantic memory tier.
 *
 * <ul>
 *   <li>{@link #remember} — supersede any conflicting fact and insert the new one. Called by
 *       consolidation when conversation turns age out.</li>
 *   <li>{@link #recall} — return the top-K most relevant active facts in scope by keyword overlap,
 *       reinforcing them so useful memories survive decay. Called per turn to inject memory.</li>
 * </ul>
 *
 * Vector embeddings were removed with the pgvector migration; recall now ranks facts by keyword
 * overlap against the query (embeddings/vector search are no longer part of this tier).
 */
@Service
@Slf4j
public class SemanticMemoryService {

    private final SemanticFactRepository repository;

    public SemanticMemoryService(SemanticFactRepository repository) {
        this.repository = repository;
    }

    /**
     * Persist a learned fact for {@code (userId, assistantId)}. A prior active fact with the same
     * subject+predicate is superseded so memory holds the latest value rather than duplicates.
     */
    public void remember(SemanticFact fact) {
        if (fact == null || isBlank(fact.getSubject()) || isBlank(fact.getPredicate())
                || isBlank(fact.getObject())) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        if (fact.getImportance() <= 0f) {
            fact.setImportance(1.0f);
        }
        try {
            repository.supersede(fact.getUserId(), fact.getAssistantId(),
                    fact.getSubject(), fact.getPredicate());
            repository.insert(fact, now);
        } catch (RuntimeException e) {
            log.warn("Failed to remember fact [{} {} {}]: {}",
                    fact.getSubject(), fact.getPredicate(), fact.getObject(), e.getMessage());
        }
    }

    /**
     * Top-K active facts relevant to {@code queryText} for the given scope, ranked by keyword overlap.
     * Scope is the user's active facts that are either global (no assistant) or bound to this
     * assistant, above {@code minConfidence}. Returns an empty list when nothing matches. Recalled
     * facts are reinforced so useful memories survive decay.
     */
    public List<ScoredFact> recall(String userId, String assistantId, String queryText,
                                   int topK, double minConfidence) {
        if (isBlank(userId) || isBlank(queryText) || topK <= 0) {
            return List.of();
        }
        try {
            String[] terms = queryText.toLowerCase().split("\\W+");
            record Ranked(SemanticFact fact, int score) {}
            List<Ranked> ranked = new ArrayList<>();
            for (SemanticFact f : repository.listByUser(userId)) {
                if (f.getConfidence() < minConfidence) {
                    continue;
                }
                if (assistantId != null && f.getAssistantId() != null
                        && !assistantId.equals(f.getAssistantId())) {
                    continue; // fact bound to a different assistant
                }
                int score = keywordScore(terms, f);
                if (score > 0) {
                    ranked.add(new Ranked(f, score));
                }
            }
            List<ScoredFact> facts = ranked.stream()
                    .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                    .limit(topK)
                    .map(r -> new ScoredFact(r.fact().getId(), r.fact().getSubject(),
                            r.fact().getPredicate(), r.fact().getObject(),
                            r.fact().getConfidence(), r.fact().getImportance(), r.score()))
                    .toList();
            if (!facts.isEmpty()) {
                repository.reinforce(facts.stream().map(ScoredFact::id).toList(),
                        0.1, Instant.now().getEpochSecond());
            }
            return facts;
        } catch (RuntimeException e) {
            log.warn("Semantic recall failed for user {} assistant {}: {}", userId, assistantId, e.getMessage());
            return List.of();
        }
    }

    private static int keywordScore(String[] terms, SemanticFact f) {
        String haystack = ((f.getSubject() == null ? "" : f.getSubject()) + " "
                + (f.getPredicate() == null ? "" : f.getPredicate()) + " "
                + (f.getObject() == null ? "" : f.getObject())).toLowerCase();
        int score = 0;
        for (String term : terms) {
            if (!term.isBlank() && haystack.contains(term)) {
                score++;
            }
        }
        return score;
    }

    /** All of a user's active facts, for the manage-memories view. */
    public List<SemanticFactDto> listForUser(String userId) {
        if (isBlank(userId)) {
            return List.of();
        }
        return repository.listByUser(userId).stream()
                .map(f -> SemanticFactDto.builder()
                        .id(f.getId())
                        .assistantId(f.getAssistantId())
                        .sessionId(f.getSessionId())
                        .subject(f.getSubject())
                        .predicate(f.getPredicate())
                        .object(f.getObject())
                        .confidence(f.getConfidence())
                        .importance(f.getImportance())
                        .createdAt(f.getCreatedAt())
                        .lastAccessedAt(f.getLastAccessedAt())
                        .build())
                .toList();
    }

    /** Forget a single fact owned by the user. Returns true if a row was removed. */
    public boolean forget(String userId, String factId) {
        if (isBlank(userId) || isBlank(factId)) {
            return false;
        }
        return repository.deleteForUser(factId, userId) > 0;
    }

    /** Forget all of a user's facts. Returns the number removed. */
    public int forgetAll(String userId) {
        if (isBlank(userId)) {
            return 0;
        }
        return repository.deleteAllForUser(userId);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
