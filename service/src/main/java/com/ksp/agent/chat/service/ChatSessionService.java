package com.ksp.agent.chat.service;

import com.ksp.agent.chat.dto.request.UpdateSessionRequest;
import com.ksp.agent.chat.dto.response.ChatMessageDto;
import com.ksp.agent.chat.dto.response.ChatSessionDto;
import com.ksp.agent.chat.dto.response.SupervisorSessionSummaryDto;

import java.util.List;

public interface ChatSessionService {

    ChatSessionDto create(String assistantId, boolean temporary);

    List<ChatSessionDto> list(boolean archived);

    List<ChatMessageDto> messages(String id);

    /**
     * Cross-user session listing for the supervisor/admin oversight read path (Phase 4.4) — the
     * most recently updated sessions from EVERY user, so a supervisor can find one to open via
     * {@link #messages(String)}. Caller must already hold ADMIN or SUPERVISOR (enforced by the
     * controller's {@code @PreAuthorize}); this method does not re-check.
     */
    List<SupervisorSessionSummaryDto> listAllForSupervisor(int limit);

    long assistantMessageCount(String id);

    ChatSessionDto update(String id, UpdateSessionRequest request);

    void touchAndMaybeTitle(String id, String firstMessage, String requestId, String lang);

    void truncateFrom(String id, int messageIndex);

    void delete(String id);

    String resolveAssistantId(String sessionId);

    /** The response-style id pinned to a session, or {@code null} if none is set. */
    String resolveStyleId(String sessionId);

    /** The LLM provider id pinned to a session, or {@code null} when the platform default applies. */
    String resolveProviderId(String sessionId);

    /** True when the session is a temporary chat (memory-isolated, auto-deleted after retention). */
    boolean isTemporary(String sessionId);

    /** Delete temporary chats last updated before now − retentionDays. Returns the count purged. */
    int purgeExpiredTemporary(int retentionDays);
}
