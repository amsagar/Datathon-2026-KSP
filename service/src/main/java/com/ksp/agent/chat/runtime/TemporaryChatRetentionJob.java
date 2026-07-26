package com.ksp.agent.chat.runtime;

import com.ksp.agent.chat.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deletes temporary chats once they pass the retention window (default 30 days, keyed on the
 * session's {@code updated_at}). Temporary chats are persisted and viewable like normal chats, but
 * auto-expire — this purge is what makes them temporary. See docs/TEMPORARY-CHAT-DESIGN.md.
 *
 * <p>Triggered by Catalyst Cron via {@code POST /api/v1/internal/cron/temporary-chat-purge} (see
 * {@code InternalCronController}) rather than an in-process {@code @Scheduled} job.
 */
@Component
@Slf4j
public class TemporaryChatRetentionJob {

    private final ChatSessionService chatSessionService;
    private final int retentionDays;

    public TemporaryChatRetentionJob(ChatSessionService chatSessionService,
                                     @Value("${agent.chat.temporary.retention-days:30}") int retentionDays) {
        this.chatSessionService = chatSessionService;
        this.retentionDays = retentionDays;
    }

    /** Purge expired temporary chats. Invoked by Catalyst Cron (see {@code InternalCronController}). */
    public void purge() {
        try {
            chatSessionService.purgeExpiredTemporary(retentionDays);
        } catch (RuntimeException e) {
            log.warn("Temporary chat retention purge failed: {}", e.getMessage());
        }
    }
}
