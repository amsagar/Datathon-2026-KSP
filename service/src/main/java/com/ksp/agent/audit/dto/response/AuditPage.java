package com.ksp.agent.audit.dto.response;

import java.util.List;

public record AuditPage(
        List<AuditEntryDto> items,
        long total
) {
}
