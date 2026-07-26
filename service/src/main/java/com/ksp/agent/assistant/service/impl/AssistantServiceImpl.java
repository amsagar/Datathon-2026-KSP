package com.ksp.agent.assistant.service.impl;

import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.assistant.dto.request.CreateAssistantRequest;
import com.ksp.agent.assistant.dto.request.UpdateAssistantRequest;
import com.ksp.agent.assistant.dto.response.AssistantDto;
import com.ksp.agent.assistant.dto.response.BuiltinToolDto;
import com.ksp.agent.assistant.entity.Assistant;
import com.ksp.agent.assistant.repo.AssistantRepository;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.chat.tooling.BuiltinToolCatalog;
import com.ksp.agent.document.rag.QuickMlRagService;
import com.ksp.agent.skill.platform.PlatformSkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class AssistantServiceImpl implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantServiceImpl.class);

    private final AssistantRepository repository;
    private final BuiltinToolCatalog builtinToolCatalog;
    private final PlatformSkillRegistry platformSkillRegistry;
    // RAG documents live in Catalyst QuickML (no FK to assistant), so they are not removed by the
    // ON DELETE CASCADE that clears agent_document rows. Purge them explicitly on assistant delete.
    private final QuickMlRagService quickMlRagService;
    private final ConfigAuditService configAuditService;

    public AssistantServiceImpl(AssistantRepository repository,
                                BuiltinToolCatalog builtinToolCatalog,
                                PlatformSkillRegistry platformSkillRegistry,
                                QuickMlRagService quickMlRagService,
                                ConfigAuditService configAuditService) {
        this.repository = repository;
        this.builtinToolCatalog = builtinToolCatalog;
        this.platformSkillRegistry = platformSkillRegistry;
        this.quickMlRagService = quickMlRagService;
        this.configAuditService = configAuditService;
    }

    @Override
    public List<AssistantDto> list() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public AssistantDto get(String id) {
        return toDto(requireEntity(id));
    }

    @Override
    public AssistantDto create(CreateAssistantRequest request) {
        long now = Instant.now().getEpochSecond();
        String name = request.getName() == null || request.getName().isBlank()
                ? "New assistant" : request.getName().trim();
        String prompt = request.getSystemPrompt() == null ? "" : request.getSystemPrompt();
        String id = repository.create(name, prompt, joinBuiltins(request.getBuiltinTools()),
                joinPlatformSkills(request.getPlatformSkills()), now);
        AssistantDto dto = get(id);
        configAuditService.recordRevision(ResourceType.assistant, id, id, dto.getName(), AuditAction.create,
                dto, null, ConfigAuditService.summarize(AuditAction.create, "assistant", dto.getName()));
        return dto;
    }

    @Override
    public AssistantDto update(String id, UpdateAssistantRequest request) {
        Assistant existing = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        String name = request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim() : existing.getName();
        String prompt = request.getSystemPrompt() != null ? request.getSystemPrompt() : existing.getSystemPrompt();
        String builtins = request.getBuiltinTools() != null
                ? joinBuiltins(request.getBuiltinTools()) : existing.getBuiltinTools();
        String platformSkills = request.getPlatformSkills() != null
                ? joinPlatformSkills(request.getPlatformSkills()) : existing.getPlatformSkills();
        repository.update(id, name, prompt, builtins, platformSkills, now);
        AssistantDto dto = get(id);
        configAuditService.recordRevision(ResourceType.assistant, id, id, dto.getName(), AuditAction.update,
                dto, null, ConfigAuditService.summarize(AuditAction.update, "assistant", dto.getName()));
        return dto;
    }

    @Override
    public void delete(String id) {
        Assistant existing = requireEntity(id);
        // Purge this assistant's RAG documents from QuickML before the row goes (cascade clears
        // agent_document rows but not the externally-hosted QuickML knowledge base).
        quickMlRagService.purgeAssistant(id);
        repository.delete(id);
        configAuditService.recordEvent(ResourceType.assistant, id, id, existing.getName(), AuditAction.delete,
                ConfigAuditService.summarize(AuditAction.delete, "assistant", existing.getName()));
    }

    @Override
    public List<BuiltinToolDto> builtinCatalog() {
        return builtinToolCatalog.catalog();
    }

    @Override
    public Assistant requireEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assistant not found: " + id));
    }

    @Override
    public List<String> builtinToolKeys(Assistant assistant) {
        if (assistant.getBuiltinTools() == null || assistant.getBuiltinTools().isBlank()) {
            return List.of();
        }
        return Arrays.stream(assistant.getBuiltinTools().split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    @Override
    public String defaultAssistantId() {
        return repository.findAll().stream().findFirst()
                .map(Assistant::getId)
                .orElse(null);
    }

    private String joinBuiltins(List<String> builtins) {
        if (builtins == null) {
            return "";
        }
        return String.join(",", builtins.stream().filter(b -> b != null && !b.isBlank()).map(String::trim).toList());
    }

    /**
     * null request value → null column (defaults apply). A non-null list — including an empty
     * one — becomes an explicit selection: joining [] yields "" which means "all disabled".
     */
    private String joinPlatformSkills(List<String> ids) {
        if (ids == null) {
            return null;
        }
        return String.join(",", platformSkillRegistry.sanitizeIds(ids));
    }

    @Override
    public List<String> activePlatformSkillIds(Assistant assistant) {
        return platformSkillRegistry.activeIdsFor(assistant.getPlatformSkills());
    }

    private AssistantDto toDto(Assistant a) {
        return AssistantDto.builder()
                .id(a.getId())
                .name(a.getName())
                .systemPrompt(a.getSystemPrompt())
                .builtinTools(builtinToolKeys(a))
                .platformSkills(activePlatformSkillIds(a))
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
