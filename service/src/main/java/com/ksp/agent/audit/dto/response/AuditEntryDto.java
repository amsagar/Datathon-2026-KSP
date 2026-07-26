package com.ksp.agent.audit.dto.response;

public record AuditEntryDto(
        String id,
        String actor,
        String action,
        String target,
        String details,
        Long createdAt
) {
}
