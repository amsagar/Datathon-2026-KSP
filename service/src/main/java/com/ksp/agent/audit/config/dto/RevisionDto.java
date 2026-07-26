package com.ksp.agent.audit.config.dto;

import tools.jackson.databind.JsonNode;

/**
 * {@code snapshot} is deliberately Jackson-3 ({@code tools.jackson}), not the classic Jackson-2
 * type used elsewhere in this codebase: Spring Boot 4 auto-configures {@code @RestController}
 * response serialization with Jackson 3, which has no native tree-serializer for a Jackson-2
 * {@code JsonNode} (a different class from a different major-version library) and would otherwise
 * bean-introspect it — see {@link com.ksp.agent.audit.config.service.ConfigAuditService#toDto}.
 */
public record RevisionDto(
        long id,
        String resourceType,
        String resourceId,
        String assistantId,
        int version,
        String action,
        String actor,
        JsonNode snapshot,
        String contentRef,
        String summary,
        long createdAt
) {
}
