package com.ksp.agent.tool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ToolGroupImportBackfill {

    private final ToolGroupService groupService;

    public ToolGroupImportBackfill(ToolGroupService groupService) {
        this.groupService = groupService;
    }

    /** Group openapi/postman tools that were imported before tool groups existed. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        try {
            int created = groupService.backfillUngroupedImports(null);
            if (created > 0) {
                log.info("Startup backfill created {} tool group(s) for legacy imports", created);
            }
        } catch (Exception e) {
            log.warn("Tool group import backfill skipped: {}", e.getMessage());
        }
    }
}
