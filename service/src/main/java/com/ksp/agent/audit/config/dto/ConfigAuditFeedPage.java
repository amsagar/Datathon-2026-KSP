package com.ksp.agent.audit.config.dto;

import java.util.List;

public record ConfigAuditFeedPage(
        List<ConfigAuditEventDto> items,
        long total
) {
}
