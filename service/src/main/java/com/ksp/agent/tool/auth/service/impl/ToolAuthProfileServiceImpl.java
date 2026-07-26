package com.ksp.agent.tool.auth.service.impl;

import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.tool.auth.dto.request.CreateAuthProfileRequest;
import com.ksp.agent.tool.auth.dto.request.UpdateAuthProfileRequest;
import com.ksp.agent.tool.auth.dto.response.ToolAuthProfileDto;
import com.ksp.agent.tool.auth.entity.ToolAuthProfile;
import com.ksp.agent.tool.auth.repo.ToolAuthProfileRepository;
import com.ksp.agent.tool.auth.service.EncryptionService;
import com.ksp.agent.tool.auth.service.ToolAuthProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class ToolAuthProfileServiceImpl implements ToolAuthProfileService {

    private final ToolAuthProfileRepository repository;
    private final EncryptionService encryptionService;
    private final ConfigAuditService configAuditService;

    public ToolAuthProfileServiceImpl(ToolAuthProfileRepository repository,
                                      EncryptionService encryptionService,
                                      ConfigAuditService configAuditService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.configAuditService = configAuditService;
    }

    @Override
    public List<ToolAuthProfileDto> list(String assistantId) {
        return repository.findByAssistant(assistantId).stream().map(this::toDto).toList();
    }

    @Override
    public ToolAuthProfileDto get(String id) {
        return toDto(requireEntity(id));
    }

    @Override
    public ToolAuthProfileDto create(String assistantId, CreateAuthProfileRequest request) {
        long now = Instant.now().getEpochSecond();
        ToolAuthProfile p = new ToolAuthProfile();
        p.setAssistantId(assistantId);
        p.setName(required(request.getName()));
        p.setDescription(request.getDescription());
        p.setAuthType(request.getAuthType() == null ? "none" : request.getAuthType());
        p.setAuthConfig(request.getAuthConfig());
        p.setEncryptedClientSecret(encryptIfPresent(request.getClientSecret()));
        p.setTokenUrl(request.getTokenUrl());
        p.setScopes(request.getScopes());
        String id = repository.create(p, now);
        log.info("Created auth profile {} ({})", id, p.getName());
        configAuditService.recordEvent(ResourceType.tool_auth, id, p.getAssistantId(), p.getName(),
                AuditAction.create, ConfigAuditService.summarize(AuditAction.create, "auth profile", p.getName()));
        return get(id);
    }

    @Override
    public ToolAuthProfileDto update(String id, UpdateAuthProfileRequest request) {
        ToolAuthProfile existing = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        if (request.getName() != null && !request.getName().isBlank()) {
            existing.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getAuthType() != null) {
            existing.setAuthType(request.getAuthType());
        }
        if (request.getAuthConfig() != null) {
            existing.setAuthConfig(request.getAuthConfig());
        }
        if (request.getTokenUrl() != null) {
            existing.setTokenUrl(request.getTokenUrl());
        }
        if (request.getScopes() != null) {
            existing.setScopes(request.getScopes());
        }
        if (request.getClientSecret() != null && !request.getClientSecret().isBlank()) {
            existing.setEncryptedClientSecret(encryptionService.encrypt(request.getClientSecret()));
        }
        repository.update(existing, now);
        configAuditService.recordEvent(ResourceType.tool_auth, id, existing.getAssistantId(), existing.getName(),
                AuditAction.update, ConfigAuditService.summarize(AuditAction.update, "auth profile", existing.getName()));
        return get(id);
    }

    @Override
    public void delete(String id) {
        ToolAuthProfile existing = requireEntity(id);
        repository.delete(id);
        log.info("Deleted auth profile {}", id);
        configAuditService.recordEvent(ResourceType.tool_auth, id, existing.getAssistantId(), existing.getName(),
                AuditAction.delete, ConfigAuditService.summarize(AuditAction.delete, "auth profile", existing.getName()));
    }

    @Override
    public ToolAuthProfile requireEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auth profile not found: " + id));
    }

    private String encryptIfPresent(String value) {
        return value == null || value.isBlank() ? null : encryptionService.encrypt(value);
    }

    private ToolAuthProfileDto toDto(ToolAuthProfile p) {
        return ToolAuthProfileDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .authType(p.getAuthType())
                .authConfig(p.getAuthConfig())
                .tokenUrl(p.getTokenUrl())
                .scopes(p.getScopes())
                .hasClientSecret(p.getEncryptedClientSecret() != null && !p.getEncryptedClientSecret().isBlank())
                .hasAccessToken(p.getEncryptedAccessToken() != null && !p.getEncryptedAccessToken().isBlank())
                .tokenExpiresAt(p.getTokenExpiresAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Auth profile name is required");
        }
        return value.trim();
    }
}
