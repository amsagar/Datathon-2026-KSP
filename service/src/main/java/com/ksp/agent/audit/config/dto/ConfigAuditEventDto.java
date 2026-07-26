package com.ksp.agent.audit.config.dto;

public record ConfigAuditEventDto(
        long id,
        String resourceType,
        String resourceId,
        String assistantId,
        String resourceName,
        String action,
        String actor,
        String summary,
        long createdAt
) {
}
