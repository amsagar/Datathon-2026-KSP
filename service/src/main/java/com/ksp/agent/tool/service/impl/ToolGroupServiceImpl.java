package com.ksp.agent.tool.service.impl;

import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.tool.dto.request.CreateToolGroupRequest;
import com.ksp.agent.tool.dto.request.UpdateToolGroupRequest;
import com.ksp.agent.tool.dto.response.ToolGroupDto;
import com.ksp.agent.tool.entity.AgentTool;
import com.ksp.agent.tool.entity.AgentToolGroup;
import com.ksp.agent.tool.repo.AgentToolGroupRepository;
import com.ksp.agent.tool.repo.AgentToolRepository;
import com.ksp.agent.tool.service.ToolGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class ToolGroupServiceImpl implements ToolGroupService {

    private final AgentToolGroupRepository repository;
    private final AgentToolRepository toolRepository;
    private final ConfigAuditService configAuditService;

    public ToolGroupServiceImpl(AgentToolGroupRepository repository,
                                AgentToolRepository toolRepository,
                                ConfigAuditService configAuditService) {
        this.repository = repository;
        this.toolRepository = toolRepository;
        this.configAuditService = configAuditService;
    }

    @Override
    public List<ToolGroupDto> list(String assistantId) {
        return repository.findByAssistant(assistantId).stream().map(this::toDto).toList();
    }

    @Override
    public ToolGroupDto get(String id) {
        return toDto(requireEntity(id));
    }

    @Override
    public ToolGroupDto create(String assistantId, CreateToolGroupRequest request) {
        long now = Instant.now().getEpochSecond();
        AgentToolGroup group = new AgentToolGroup();
        group.setAssistantId(required(assistantId, "assistantId"));
        group.setName(required(request.getName(), "name"));
        group.setDescription(request.getDescription());
        group.setSourceType("manual");
        group.setEnabled(request.getEnabled() == null || request.getEnabled());
        String id = repository.create(group, now);
        log.info("Created tool group {} ({})", id, group.getName());
        configAuditService.recordEvent(ResourceType.tool_group, id, assistantId, group.getName(),
                AuditAction.create, ConfigAuditService.summarize(AuditAction.create, "tool group", group.getName()));
        return get(id);
    }

    @Override
    public ToolGroupDto createImported(String assistantId, String name, String description, String sourceType) {
        long now = Instant.now().getEpochSecond();
        AgentToolGroup group = new AgentToolGroup();
        group.setAssistantId(required(assistantId, "assistantId"));
        group.setName(name == null || name.isBlank() ? "Imported tools" : name.trim());
        group.setDescription(description);
        group.setSourceType(sourceType == null || sourceType.isBlank() ? "manual" : sourceType);
        group.setEnabled(true);
        String id = repository.create(group, now);
        log.info("Created imported tool group {} ({})", id, group.getName());
        configAuditService.recordEvent(ResourceType.tool_group, id, group.getAssistantId(), group.getName(),
                AuditAction.create, ConfigAuditService.summarize(AuditAction.create, "tool group", group.getName()));
        return get(id);
    }

    @Override
    public ToolGroupDto update(String id, UpdateToolGroupRequest request) {
        AgentToolGroup existing = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        if (request.getName() != null && !request.getName().isBlank()) {
            existing.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        repository.update(existing, now);
        AuditAction action = request.getEnabled() != null
                ? ConfigAuditService.toggleAction(request.getEnabled()) : AuditAction.update;
        configAuditService.recordEvent(ResourceType.tool_group, id, existing.getAssistantId(), existing.getName(),
                action, ConfigAuditService.summarize(action, "tool group", existing.getName()));
        return get(id);
    }

    @Override
    public void delete(String id) {
        AgentToolGroup existing = requireEntity(id);
        repository.delete(id);
        log.info("Deleted tool group {}", id);
        configAuditService.recordEvent(ResourceType.tool_group, id, existing.getAssistantId(), existing.getName(),
                AuditAction.delete, ConfigAuditService.summarize(AuditAction.delete, "tool group", existing.getName()));
    }

    @Override
    public int backfillUngroupedImports(String assistantId) {
        List<AgentTool> ungrouped = toolRepository.findUngroupedImports();
        if (assistantId != null && !assistantId.isBlank()) {
            String id = assistantId.trim();
            ungrouped = ungrouped.stream().filter(t -> id.equals(t.getAssistantId())).toList();
        }
        if (ungrouped.isEmpty()) {
            return 0;
        }

        Map<String, List<AgentTool>> batches = new LinkedHashMap<>();
        for (AgentTool tool : ungrouped) {
            String key = tool.getAssistantId() + "|" + tool.getSourceType() + "|"
                    + normalizeHostKey(tool.getHost());
            batches.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tool);
        }

        long now = Instant.now().getEpochSecond();
        int groupsCreated = 0;
        for (List<AgentTool> batch : batches.values()) {
            AgentTool sample = batch.get(0);
            ToolGroupDto group = createImported(
                    sample.getAssistantId(),
                    legacyGroupName(sample.getSourceType(), sample.getHost()),
                    "Migrated from a previous import",
                    sample.getSourceType());
            for (AgentTool tool : batch) {
                tool.setGroupId(group.getId());
                toolRepository.update(tool, now);
            }
            groupsCreated++;
        }
        log.info("Backfilled {} tool group(s) for {} legacy import tool(s)",
                groupsCreated, ungrouped.size());
        return groupsCreated;
    }

    @Override
    public AgentToolGroup requireEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tool group not found: " + id));
    }

    private ToolGroupDto toDto(AgentToolGroup group) {
        return ToolGroupDto.builder()
                .id(group.getId())
                .assistantId(group.getAssistantId())
                .name(group.getName())
                .description(group.getDescription())
                .sourceType(group.getSourceType())
                .enabled(group.isEnabled())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tool group " + field + " is required");
        }
        return value.trim();
    }

    private static String normalizeHostKey(String host) {
        return host == null || host.isBlank() ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    private static String legacyGroupName(String sourceType, String host) {
        String base = switch (sourceType == null ? "" : sourceType) {
            case "openapi_import" -> "OpenAPI import";
            case "postman_import" -> "Postman collection";
            default -> "Imported tools";
        };
        if (host == null || host.isBlank()) {
            return base;
        }
        return base + " · " + host.trim();
    }
}
