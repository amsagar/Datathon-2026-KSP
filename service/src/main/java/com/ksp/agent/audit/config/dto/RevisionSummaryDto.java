package com.ksp.agent.audit.config.dto;

public record RevisionSummaryDto(
        long id,
        int version,
        String action,
        String actor,
        String summary,
        boolean hasContent,
        long createdAt
) {
}
