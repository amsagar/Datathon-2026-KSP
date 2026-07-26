package com.ksp.agent.memory.dto.response;

import lombok.Builder;
import lombok.Data;

/** A long-term memory fact as surfaced to the manage-memories UI. */
@Data
@Builder
public class SemanticFactDto {
    private String id;
    private String assistantId;
    private String sessionId;
    private String subject;
    private String predicate;
    private String object;
    private float confidence;
    private float importance;
    private Long createdAt;
    private Long lastAccessedAt;
}
