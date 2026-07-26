package com.ksp.agent.memory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A durable, atomic fact the agent has learned (subject-predicate-object), forming the long-term
 * semantic memory tier. Unlike the session-scoped transcript/summary, facts persist across all of a
 * user's conversations and are recalled by vector similarity into future turns.
 *
 * <p>Scope: {@code userId} is the owner (NULL = assistant-shared knowledge); {@code assistantId}
 * narrows the scope (NULL = applies across all the user's assistants). {@code importance} decays over
 * time and is reinforced on recall; a conflicting newer fact flips {@code superseded} on the old one.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SemanticFact {
    private String id;
    private String userId;
    private String assistantId;
    private String sessionId;
    private String subject;
    private String predicate;
    private String object;
    private float confidence;
    private float importance;
    private boolean superseded;
    private Long createdAt;
    private Long lastAccessedAt;
}
