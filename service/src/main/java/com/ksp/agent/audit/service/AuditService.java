package com.ksp.agent.audit.service;

import com.ksp.agent.audit.dto.response.AuditEntryDto;
import com.ksp.agent.audit.dto.response.AuditPage;
import com.ksp.agent.audit.entity.AuditLog;
import com.ksp.agent.audit.repo.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Best-effort audit write. Never propagates failures — auditing must not break the
     * business operation that triggered it.
     */
    public void record(String actor, String action, String target, String details) {
        try {
            repository.insert(actor, action, target, details, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Failed to write audit entry action={} target={}: {}", action, target, e.getMessage());
        }
    }

    public AuditPage recent(String search, String action, int limit, int offset) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        int safeOffset = Math.max(offset, 0);
        List<AuditEntryDto> items = repository.findRecent(search, action, safeLimit, safeOffset)
                .stream().map(this::toDto).toList();
        long total = repository.count(search, action);
        return new AuditPage(items, total);
    }

    private AuditEntryDto toDto(AuditLog a) {
        return new AuditEntryDto(a.getId(), a.getActor(), a.getAction(),
                a.getTarget(), a.getDetails(), a.getCreatedAt());
    }
}
