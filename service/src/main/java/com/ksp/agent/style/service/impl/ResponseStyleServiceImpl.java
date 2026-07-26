package com.ksp.agent.style.service.impl;

import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.style.dto.request.CreateStyleRequest;
import com.ksp.agent.style.dto.request.UpdateStyleRequest;
import com.ksp.agent.style.dto.response.ResponseStyleDto;
import com.ksp.agent.style.entity.ResponseStyle;
import com.ksp.agent.style.repo.ResponseStyleRepository;
import com.ksp.agent.style.service.ResponseStyleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class ResponseStyleServiceImpl implements ResponseStyleService {

    private final ResponseStyleRepository repository;
    private final ConfigAuditService configAuditService;

    public ResponseStyleServiceImpl(ResponseStyleRepository repository, ConfigAuditService configAuditService) {
        this.repository = repository;
        this.configAuditService = configAuditService;
    }

    @Override
    public List<ResponseStyleDto> list(String assistantId) {
        return repository.findByAssistant(assistantId).stream().map(this::toDto).toList();
    }

    @Override
    public ResponseStyleDto get(String id) {
        return toDto(requireEntity(id));
    }

    @Override
    public ResponseStyleDto create(String assistantId, CreateStyleRequest request) {
        long now = Instant.now().getEpochSecond();
        ResponseStyle s = new ResponseStyle();
        s.setAssistantId(assistantId);
        s.setName(required(request.getName(), "name"));
        s.setDescription(request.getDescription());
        s.setInstructions(required(request.getInstructions(), "instructions"));
        s.setDefaultStyle(false);
        String id = repository.create(s, now);
        log.info("Created response style {} ({})", id, s.getName());
        ResponseStyleDto dto = get(id);
        configAuditService.recordRevision(ResourceType.response_style, id, assistantId, dto.getName(),
                AuditAction.create, dto, null,
                ConfigAuditService.summarize(AuditAction.create, "response style", dto.getName()));
        return dto;
    }

    @Override
    public ResponseStyleDto update(String id, UpdateStyleRequest request) {
        ResponseStyle existing = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        if (request.getName() != null && !request.getName().isBlank()) {
            existing.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getInstructions() != null && !request.getInstructions().isBlank()) {
            existing.setInstructions(request.getInstructions());
        }
        repository.update(existing, now);
        ResponseStyleDto dto = get(id);
        configAuditService.recordRevision(ResourceType.response_style, id, existing.getAssistantId(), dto.getName(),
                AuditAction.update, dto, null,
                ConfigAuditService.summarize(AuditAction.update, "response style", dto.getName()));
        return dto;
    }

    @Override
    public void delete(String id) {
        ResponseStyle existing = requireEntity(id);
        repository.delete(id);
        log.info("Deleted response style {}", id);
        if (existing.isDefaultStyle()) {
            log.debug("Removed default response style for assistant {}", existing.getAssistantId());
        }
        configAuditService.recordEvent(ResourceType.response_style, id, existing.getAssistantId(),
                existing.getName(), AuditAction.delete,
                ConfigAuditService.summarize(AuditAction.delete, "response style", existing.getName()));
    }

    @Override
    public ResponseStyleDto setDefault(String id) {
        ResponseStyle style = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        repository.clearDefaultForAssistant(style.getAssistantId());
        repository.setDefault(id, now);
        log.info("Set default response style {} for assistant {}", id, style.getAssistantId());
        ResponseStyleDto dto = get(id);
        configAuditService.recordRevision(ResourceType.response_style, id, style.getAssistantId(), dto.getName(),
                AuditAction.set_default, dto, null,
                ConfigAuditService.summarize(AuditAction.set_default, "response style", dto.getName()));
        return dto;
    }

    @Override
    public ResponseStyle requireEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Response style not found: " + id));
    }

    @Override
    public String instructionsFor(String styleId) {
        if (styleId == null || styleId.isBlank()) {
            return null;
        }
        return repository.findById(styleId)
                .map(ResponseStyle::getInstructions)
                .filter(i -> i != null && !i.isBlank())
                .orElse(null);
    }

    @Override
    public String defaultStyleIdForAssistant(String assistantId) {
        if (assistantId == null || assistantId.isBlank()) {
            return null;
        }
        return repository.findDefaultByAssistant(assistantId)
                .map(ResponseStyle::getId)
                .orElse(null);
    }

    private ResponseStyleDto toDto(ResponseStyle s) {
        return ResponseStyleDto.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .instructions(s.getInstructions())
                .defaultStyle(s.isDefaultStyle())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Response style " + field + " is required");
        }
        return value.trim();
    }
}
