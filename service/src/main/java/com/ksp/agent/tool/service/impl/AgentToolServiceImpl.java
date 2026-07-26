package com.ksp.agent.tool.service.impl;

import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.tool.dto.request.CreateToolRequest;
import com.ksp.agent.tool.dto.request.TestToolRequest;
import com.ksp.agent.tool.dto.request.UpdateToolRequest;
import com.ksp.agent.tool.dto.response.AgentToolDto;
import com.ksp.agent.tool.dto.response.TestToolResult;
import com.ksp.agent.tool.entity.AgentTool;
import com.ksp.agent.tool.repo.AgentToolRepository;
import com.ksp.agent.tool.runtime.HttpToolExecutor;
import com.ksp.agent.tool.service.AgentToolService;
import com.ksp.agent.tool.service.ToolEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class AgentToolServiceImpl implements AgentToolService {

    private final AgentToolRepository repository;
    private final HttpToolExecutor executor;
    private final ToolEmbeddingService embeddingService;
    private final ConfigAuditService configAuditService;

    public AgentToolServiceImpl(AgentToolRepository repository, HttpToolExecutor executor,
                                ToolEmbeddingService embeddingService, ConfigAuditService configAuditService) {
        this.repository = repository;
        this.executor = executor;
        this.embeddingService = embeddingService;
        this.configAuditService = configAuditService;
    }

    @Override
    public List<AgentToolDto> list(String assistantId) {
        return repository.findByAssistant(assistantId).stream().map(this::toDto).toList();
    }

    @Override
    public AgentToolDto get(String id) {
        return toDto(requireEntity(id));
    }

    @Override
    public AgentToolDto create(String assistantId, CreateToolRequest request) {
        long now = Instant.now().getEpochSecond();
        AgentTool tool = new AgentTool();
        tool.setAssistantId(required(assistantId, "assistantId"));
        tool.setName(required(request.getName(), "name"));
        tool.setDescription(request.getDescription());
        tool.setMethod(normalizeMethod(request.getMethod()));
        tool.setHost(required(request.getHost(), "host"));
        tool.setEndpoint(request.getEndpoint() == null ? "" : request.getEndpoint());
        tool.setRequestSchema(request.getRequestSchema());
        tool.setSourceType("manual");
        tool.setAuthProfileId(blankToNull(request.getAuthProfileId()));
        tool.setAuthType(request.getAuthType() == null ? "none" : request.getAuthType());
        tool.setAuthConfig(request.getAuthConfig());
        tool.setGroupId(blankToNull(request.getGroupId()));
        tool.setEnabled(request.getEnabled() == null || request.getEnabled());
        String id = repository.create(tool, now);
        log.info("Created HTTP tool {} ({})", id, tool.getName());
        embeddingService.embedToolAsync(id);
        configAuditService.recordEvent(ResourceType.tool, id, tool.getAssistantId(), tool.getName(),
                AuditAction.create, ConfigAuditService.summarize(AuditAction.create, "tool", tool.getName()));
        return get(id);
    }

    @Override
    public AgentToolDto update(String id, UpdateToolRequest request) {
        AgentTool existing = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        if (request.getName() != null && !request.getName().isBlank()) {
            existing.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getMethod() != null) {
            existing.setMethod(normalizeMethod(request.getMethod()));
        }
        if (request.getHost() != null && !request.getHost().isBlank()) {
            existing.setHost(request.getHost().trim());
        }
        if (request.getEndpoint() != null) {
            existing.setEndpoint(request.getEndpoint());
        }
        if (request.getRequestSchema() != null) {
            existing.setRequestSchema(request.getRequestSchema());
        }
        if (request.getAuthProfileId() != null) {
            existing.setAuthProfileId(blankToNull(request.getAuthProfileId()));
        }
        if (request.getAuthType() != null) {
            existing.setAuthType(request.getAuthType());
        }
        if (request.getAuthConfig() != null) {
            existing.setAuthConfig(request.getAuthConfig());
        }
        if (request.getGroupId() != null) {
            existing.setGroupId(blankToNull(request.getGroupId()));
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        repository.update(existing, now);
        embeddingService.embedToolAsync(id);
        AuditAction action = request.getEnabled() != null
                ? ConfigAuditService.toggleAction(request.getEnabled()) : AuditAction.update;
        configAuditService.recordEvent(ResourceType.tool, id, existing.getAssistantId(), existing.getName(),
                action, ConfigAuditService.summarize(action, "tool", existing.getName()));
        return get(id);
    }

    @Override
    public void delete(String id) {
        AgentTool existing = requireEntity(id);
        repository.delete(id);
        log.info("Deleted HTTP tool {}", id);
        configAuditService.recordEvent(ResourceType.tool, id, existing.getAssistantId(), existing.getName(),
                AuditAction.delete, ConfigAuditService.summarize(AuditAction.delete, "tool", existing.getName()));
    }

    @Override
    public TestToolResult test(String id, TestToolRequest request) {
        AgentTool tool = requireEntity(id);
        String input = request == null || request.getInput() == null ? "{}" : request.getInput();
        try {
            return new TestToolResult(true, executor.execute(tool, input));
        } catch (RuntimeException e) {
            return new TestToolResult(false, e.getMessage());
        }
    }

    @Override
    public AgentTool requireEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + id));
    }

    @Override
    public AgentToolDto persistImported(String assistantId, AgentTool tool) {
        long now = Instant.now().getEpochSecond();
        tool.setAssistantId(required(assistantId, "assistantId"));
        if (tool.getMethod() == null || tool.getMethod().isBlank()) {
            tool.setMethod("GET");
        }
        if (tool.getAuthType() == null) {
            tool.setAuthType("none");
        }
        tool.setEnabled(true);
        String id = repository.create(tool, now);
        embeddingService.embedToolAsync(id);
        return get(id);
    }

    private AgentToolDto toDto(AgentTool t) {
        return AgentToolDto.builder()
                .id(t.getId())
                .assistantId(t.getAssistantId())
                .name(t.getName())
                .description(t.getDescription())
                .method(t.getMethod())
                .host(t.getHost())
                .endpoint(t.getEndpoint())
                .requestSchema(t.getRequestSchema())
                .sourceType(t.getSourceType())
                .authProfileId(t.getAuthProfileId())
                .authType(t.getAuthType())
                .authConfig(t.getAuthConfig())
                .groupId(t.getGroupId())
                .enabled(t.isEnabled())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private static String normalizeMethod(String method) {
        return method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tool " + field + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
