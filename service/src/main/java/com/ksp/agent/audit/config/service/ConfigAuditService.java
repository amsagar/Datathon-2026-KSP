package com.ksp.agent.audit.config.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.dto.ConfigAuditEventDto;
import com.ksp.agent.audit.config.dto.ConfigAuditFeedPage;
import com.ksp.agent.audit.config.dto.RevisionDto;
import com.ksp.agent.audit.config.dto.RevisionSummaryDto;
import com.ksp.agent.audit.config.entity.ConfigAuditEvent;
import com.ksp.agent.audit.config.entity.ConfigRevision;
import com.ksp.agent.audit.config.repo.ConfigAuditEventRepository;
import com.ksp.agent.audit.config.repo.ConfigRevisionRepository;
import com.ksp.agent.auth.service.SecurityContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Records config-resource audit feed events and full version snapshots ({@code config_revision}).
 * Mirrors {@link com.ksp.agent.audit.service.AuditService}'s best-effort style (never propagates
 * failures — auditing must not break the business operation that triggered it) but lives in its
 * own {@code audit.config} sub-package to stay clearly separate from the flat, pre-existing
 * {@code user_audit_log} concern.
 */
@Service
public class ConfigAuditService {

    private static final Logger log = LoggerFactory.getLogger(ConfigAuditService.class);

    private final ConfigAuditEventRepository eventRepository;
    private final ConfigRevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;
    private final tools.jackson.databind.ObjectMapper jackson3Mapper;
    private final SecurityContextService securityContextService;

    public ConfigAuditService(ConfigAuditEventRepository eventRepository,
                              ConfigRevisionRepository revisionRepository,
                              ObjectMapper objectMapper,
                              tools.jackson.databind.ObjectMapper jackson3Mapper,
                              SecurityContextService securityContextService) {
        this.eventRepository = eventRepository;
        this.revisionRepository = revisionRepository;
        this.objectMapper = objectMapper;
        this.jackson3Mapper = jackson3Mapper;
        this.securityContextService = securityContextService;
    }

    /** Best-effort feed-event write. Never throws. */
    public void recordEvent(ResourceType type, String resourceId, String assistantId, String resourceName,
                            AuditAction action, String summary) {
        try {
            eventRepository.insert(type.name(), resourceId, assistantId, resourceName, action.name(),
                    actor(), summary, Instant.now().getEpochSecond());
        } catch (Exception e) {
            log.warn("Failed to write config audit event action={} type={} resourceId={}: {}",
                    action, type, resourceId, e.getMessage());
        }
    }

    /**
     * Best-effort revision snapshot write, followed by a matching feed event. The version number
     * is {@code max(existing)+1}; on a concurrent-writer race against the unique
     * {@code (resource_type, resource_id, version)} constraint, the max is recomputed and the
     * insert retried once before giving up.
     */
    public void recordRevision(ResourceType type, String resourceId, String assistantId, String resourceName,
                               AuditAction action, Object snapshotDto, String contentRef, String summary) {
        String actor = actor();
        long now = Instant.now().getEpochSecond();
        try {
            String snapshotJson = objectMapper.writeValueAsString(snapshotDto);
            insertWithRetry(type, resourceId, assistantId, action, actor, snapshotJson, contentRef, summary, now);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize snapshot for {} {}: {}", type, resourceId, e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to record revision for {} {}: {}", type, resourceId, e.getMessage());
        }
        recordEvent(type, resourceId, assistantId, resourceName, action, summary);
    }

    private void insertWithRetry(ResourceType type, String resourceId, String assistantId, AuditAction action,
                                 String actor, String snapshotJson, String contentRef, String summary, long now) {
        int version = revisionRepository.findMaxVersion(type.name(), resourceId) + 1;
        try {
            revisionRepository.insert(type.name(), resourceId, assistantId, version, action.name(), actor,
                    snapshotJson, contentRef, summary, now);
        } catch (DuplicateKeyException e) {
            int retryVersion = revisionRepository.findMaxVersion(type.name(), resourceId) + 1;
            revisionRepository.insert(type.name(), resourceId, assistantId, retryVersion, action.name(), actor,
                    snapshotJson, contentRef, summary, now);
        }
    }

    /** Writes a v1 "Baseline snapshot" revision only if the resource has none yet (idempotent). */
    public void seedRevisionIfAbsent(ResourceType type, String resourceId, String assistantId, Object snapshotDto) {
        try {
            if (revisionRepository.count(type.name(), resourceId) > 0) {
                return;
            }
            String snapshotJson = objectMapper.writeValueAsString(snapshotDto);
            revisionRepository.insert(type.name(), resourceId, assistantId, 1, AuditAction.create.name(),
                    "system", snapshotJson, null, "Baseline snapshot", Instant.now().getEpochSecond());
        } catch (DuplicateKeyException e) {
            log.debug("Baseline revision for {} {} already seeded by a concurrent pass", type, resourceId);
        } catch (Exception e) {
            log.warn("Failed to seed baseline revision for {} {}: {}", type, resourceId, e.getMessage());
        }
    }

    public ConfigAuditFeedPage feed(ResourceType type, String actor, String resourceId, Long from, Long to,
                                    int page, int size) {
        int safeSize = size <= 0 ? 50 : Math.min(size, 500);
        int safePage = Math.max(page, 0);
        String typeFilter = type == null ? null : type.name();
        List<ConfigAuditEventDto> items = eventRepository
                .findFeed(typeFilter, actor, resourceId, from, to, safeSize, safePage * safeSize)
                .stream().map(this::toDto).toList();
        long total = eventRepository.countFeed(typeFilter, actor, resourceId, from, to);
        return new ConfigAuditFeedPage(items, total);
    }

    public List<RevisionSummaryDto> revisionSummaries(ResourceType type, String resourceId) {
        return revisionRepository.findSummaries(type.name(), resourceId);
    }

    public Optional<RevisionDto> revision(ResourceType type, String resourceId, int version) {
        return revisionRepository.findOne(type.name(), resourceId, version).map(this::toDto);
    }

    public RevisionDto toDto(ConfigRevision r) {
        return new RevisionDto(r.getId(), r.getResourceType(), r.getResourceId(), r.getAssistantId(),
                r.getVersion(), r.getAction(), r.getActor(), toJackson3Tree(r.getSnapshot()), r.getContentRef(),
                r.getSummary(), r.getCreatedAt());
    }

    /**
     * {@code ConfigRevision.snapshot} is parsed with the classic Jackson-2 {@link ObjectMapper} (used
     * internally throughout this codebase, including {@link com.ksp.agent.audit.config.service.ConfigRevertService}'s
     * revert-by-replay logic). {@link RevisionDto#snapshot}, however, must be Jackson-3 to serialize
     * correctly over the wire (see the type's own javadoc) — round-tripping through a JSON string is
     * the simplest way to hand the same tree content to a different major-version Jackson without
     * touching the Jackson-2 convention used everywhere else.
     */
    private tools.jackson.databind.JsonNode toJackson3Tree(com.fasterxml.jackson.databind.JsonNode snapshot) {
        if (snapshot == null) {
            return null;
        }
        return jackson3Mapper.readTree(snapshot.toString());
    }

    private ConfigAuditEventDto toDto(ConfigAuditEvent e) {
        return new ConfigAuditEventDto(e.getId(), e.getResourceType(), e.getResourceId(), e.getAssistantId(),
                e.getResourceName(), e.getAction(), e.getActor(), e.getSummary(), e.getCreatedAt());
    }

    private String actor() {
        try {
            return securityContextService.currentUserIdOrThrow();
        } catch (Exception e) {
            return "system";
        }
    }

    public static AuditAction toggleAction(Boolean enabled) {
        if (enabled == null) {
            return AuditAction.update;
        }
        return enabled ? AuditAction.enable : AuditAction.disable;
    }

    public static String summarize(AuditAction action, String noun, String name) {
        String verb = switch (action) {
            case create -> "Created";
            case update -> "Updated";
            case delete -> "Deleted";
            case enable -> "Enabled";
            case disable -> "Disabled";
            case set_default -> "Set default";
            case discover -> "Discovered tools for";
            case file_edit -> "Edited a file in";
            case tool_enable -> "Enabled";
            case tool_disable -> "Disabled";
            case revert -> "Reverted";
        };
        return verb + " " + noun + " " + name;
    }
}
