package com.ksp.agent.audit.config.service;

import com.ksp.agent.assistant.dto.response.AssistantDto;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.skill.dto.response.SkillDto;
import com.ksp.agent.skill.service.SkillService;
import com.ksp.agent.style.dto.response.ResponseStyleDto;
import com.ksp.agent.style.service.ResponseStyleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Seeds a v1 "Baseline snapshot" {@code config_revision} for every existing versioned resource
 * (assistant/skill/response_style) that predates this feature shipping, so nothing
 * shows "no versions" forever. Idempotent — {@link ConfigAuditService#seedRevisionIfAbsent} is a
 * no-op if a resource already has revisions. Mirrors {@code ToolGroupImportBackfill}'s
 * {@code @Async @EventListener(ApplicationReadyEvent.class)} convention.
 */
@Component
@Slf4j
public class ConfigRevisionBackfill {

    private final AssistantService assistantService;
    private final SkillService skillService;
    private final ResponseStyleService responseStyleService;
    private final ConfigAuditService configAuditService;

    public ConfigRevisionBackfill(AssistantService assistantService,
                                  SkillService skillService,
                                  ResponseStyleService responseStyleService,
                                  ConfigAuditService configAuditService) {
        this.assistantService = assistantService;
        this.skillService = skillService;
        this.responseStyleService = responseStyleService;
        this.configAuditService = configAuditService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        try {
            int seeded = 0;
            for (AssistantDto assistant : assistantService.list()) {
                String assistantId = assistant.getId();
                configAuditService.seedRevisionIfAbsent(ResourceType.assistant, assistantId, assistantId, assistant);
                seeded++;

                for (SkillDto skill : skillService.list(assistantId)) {
                    configAuditService.seedRevisionIfAbsent(ResourceType.skill, skill.getId(), assistantId, skill);
                    seeded++;
                }
                for (ResponseStyleDto style : responseStyleService.list(assistantId)) {
                    configAuditService.seedRevisionIfAbsent(ResourceType.response_style, style.getId(), assistantId, style);
                    seeded++;
                }
            }
            log.info("Config revision backfill checked {} resource(s) (no-op for any that already have revisions)", seeded);
        } catch (Exception e) {
            log.warn("Config revision backfill skipped: {}", e.getMessage());
        }
    }
}
