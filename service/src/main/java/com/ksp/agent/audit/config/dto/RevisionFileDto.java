package com.ksp.agent.audit.config.dto;

public record RevisionFileDto(
        String path,
        String content,
        boolean binary
) {
}
