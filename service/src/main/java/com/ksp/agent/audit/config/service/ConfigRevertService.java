package com.ksp.agent.audit.config.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.assistant.dto.request.UpdateAssistantRequest;
import com.ksp.agent.assistant.dto.response.AssistantDto;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.dto.RevisionDto;
import com.ksp.agent.audit.config.entity.ConfigRevision;
import com.ksp.agent.audit.config.repo.ConfigRevisionRepository;
import com.ksp.agent.skill.dto.response.SkillDto;
import com.ksp.agent.skill.service.SkillService;
import com.ksp.agent.style.dto.request.UpdateStyleRequest;
import com.ksp.agent.style.dto.response.ResponseStyleDto;
import com.ksp.agent.style.service.ResponseStyleService;
import org.springframework.stereotype.Service;

/**
 * Reverts a versioned config resource (assistant/response_style/skill) to a prior
 * {@code config_revision} snapshot.
 *
 * <p>For assistant/response_style this simply replays the snapshot through the same
 * {@code .update(id, request)} method the PUT endpoint already calls, so existing validation and a
 * fresh revision are recorded automatically. Skill revert is special-cased (see
 * {@link SkillService#revertToVersion}) because a skill's live content lives in blob storage, not
 * just DB columns.
 */
@Service
public class ConfigRevertService {

    private final ConfigRevisionRepository revisionRepository;
    private final ConfigAuditService configAuditService;
    private final AssistantService assistantService;
    private final ResponseStyleService responseStyleService;
    private final SkillService skillService;

    /**
     * Locally-scoped lenient mapper: each snapshot is a response DTO (carries extra fields such as
     * {@code id}/{@code createdAt} absent from the update-request DTOs), and the global
     * {@code ObjectMapper} bean (see JacksonConfig) defaults to strict unknown-property rejection.
     * A naive {@code convertValue} against the global mapper would throw
     * {@code UnrecognizedPropertyException}, so this class builds its own copy instead of touching
     * the shared bean.
     */
    private final ObjectMapper lenientMapper;

    public ConfigRevertService(ConfigRevisionRepository revisionRepository,
                              ObjectMapper objectMapper,
                              ConfigAuditService configAuditService,
                              AssistantService assistantService,
                              ResponseStyleService responseStyleService,
                              SkillService skillService) {
        this.revisionRepository = revisionRepository;
        this.configAuditService = configAuditService;
        this.assistantService = assistantService;
        this.responseStyleService = responseStyleService;
        this.skillService = skillService;
        this.lenientMapper = objectMapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public void revert(ResourceType type, String resourceId, int version) {
        if (!type.isVersioned()) {
            throw new IllegalArgumentException("Revert is not supported for resource type " + type);
        }
        ConfigRevision revision = revisionRepository.findOne(type.name(), resourceId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Revision v" + version + " not found for " + type + " " + resourceId));
        if (revision.getSnapshot() == null) {
            throw new IllegalArgumentException("Revision v" + version + " has no snapshot to revert to");
        }

        String assistantId = revision.getAssistantId();
        String name;
        switch (type) {
            case assistant -> {
                UpdateAssistantRequest request =
                        lenientMapper.convertValue(revision.getSnapshot(), UpdateAssistantRequest.class);
                AssistantDto dto = assistantService.update(resourceId, request);
                name = dto.getName();
                assistantId = resourceId;
            }
            case response_style -> {
                UpdateStyleRequest request =
                        lenientMapper.convertValue(revision.getSnapshot(), UpdateStyleRequest.class);
                ResponseStyleDto dto = responseStyleService.update(resourceId, request);
                name = dto.getName();
            }
            case skill -> {
                RevisionDto revisionDto = configAuditService.toDto(revision);
                SkillDto dto = skillService.revertToVersion(resourceId, revisionDto);
                name = dto.getName();
            }
            default -> throw new IllegalArgumentException("Revert is not supported for resource type " + type);
        }

        configAuditService.recordEvent(type, resourceId, assistantId, name, AuditAction.revert,
                "Reverted to v" + version);
    }
}
