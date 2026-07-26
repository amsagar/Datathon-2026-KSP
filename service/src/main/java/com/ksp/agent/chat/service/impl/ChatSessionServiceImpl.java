package com.ksp.agent.chat.service.impl;

import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.audit.service.AuditService;
import com.ksp.agent.chat.dto.request.UpdateSessionRequest;
import com.ksp.agent.chat.dto.response.ChatMessageDto;
import com.ksp.agent.chat.dto.response.ChatSessionDto;
import com.ksp.agent.chat.dto.response.SupervisorSessionSummaryDto;
import com.ksp.agent.chat.dto.response.ToolCallDto;
import com.ksp.agent.chat.entity.ChatSession;
import com.ksp.agent.chat.entity.ChatToolEvent;
import com.ksp.agent.chat.repo.ChatSessionRepository;
import com.ksp.agent.chat.repo.ChatToolEventRepository;
import com.ksp.agent.chat.repo.ChatTranscriptRepository;
import com.ksp.agent.chat.service.ChatSessionService;
import com.ksp.agent.chat.usage.LlmUsageContext;
import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.style.service.ResponseStyleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final String DEFAULT_TITLE = "New chat";

    private final ChatSessionRepository repository;
    private final ChatToolEventRepository toolEventRepository;
    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatTranscriptRepository chatTranscriptRepository;
    private final SessionTitleService sessionTitleService;
    private final AssistantService assistantService;
    private final SecurityContextService securityContextService;
    private final ResponseStyleService responseStyleService;
    private final AuditService auditService;

    public ChatSessionServiceImpl(ChatSessionRepository repository,
                                  ChatToolEventRepository toolEventRepository,
                                  ChatMemory chatMemory,
                                  ChatMemoryRepository chatMemoryRepository,
                                  ChatTranscriptRepository chatTranscriptRepository,
                                  SessionTitleService sessionTitleService,
                                  AssistantService assistantService,
                                  SecurityContextService securityContextService,
                                  ResponseStyleService responseStyleService,
                                  AuditService auditService) {
        this.repository = repository;
        this.toolEventRepository = toolEventRepository;
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatTranscriptRepository = chatTranscriptRepository;
        this.auditService = auditService;
        this.sessionTitleService = sessionTitleService;
        this.assistantService = assistantService;
        this.securityContextService = securityContextService;
        this.responseStyleService = responseStyleService;
    }

    @Override
    public ChatSessionDto create(String assistantId, boolean temporary) {
        String userId = securityContextService.currentUserIdOrThrow();
        long now = Instant.now().getEpochSecond();
        String resolvedAssistantId = (assistantId == null || assistantId.isBlank())
                ? assistantService.defaultAssistantId() : assistantId;
        String id = repository.create(DEFAULT_TITLE, resolvedAssistantId, userId, temporary, now);
        log.info("Created {}chat session {} for user {} (assistant {})",
                temporary ? "temporary " : "", id, userId, resolvedAssistantId);
        return repository.findById(id, userId)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalStateException("Session not found after create: " + id));
    }

    @Override
    public List<ChatSessionDto> list(boolean archived) {
        String userId = securityContextService.currentUserIdOrThrow();
        return repository.findByArchived(archived, userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<ChatMessageDto> messages(String id) {
        String currentUserId = securityContextService.currentUserIdOrThrow();
        if (securityContextService.hasAnyRole("ADMIN", "SUPERVISOR")) {
            // Accountability read path: a supervisor/admin may review ANY user's session + tool
            // trail (the actual SQL/tool calls a chatbot ran against the FIR database), not just
            // their own — the whole point of persisting chat_tool_event is investigator oversight.
            // findOwner has no per-user filter (existence check only); every mutating operation
            // below (update/truncate/delete) still goes through the owner-scoped requireSession.
            ChatSessionRepository.SessionOwner owner = repository.findOwner(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
            if (!currentUserId.equals(owner.userId())) {
                auditService.record(currentUserId, "VIEW_OTHER_USER_SESSION", id, owner.userId());
            }
        } else {
            requireSession(id, currentUserId);
        }

        // Tool events were persisted separately (Spring AI chat memory does not store tool
        // call/result data). Group them by the assistant-turn index they belong to so each
        // assistant message can re-render its tool cards on reload.
        Map<Integer, List<ToolCallDto>> toolsByTurn = toolEventRepository.findBySession(id).stream()
                .collect(Collectors.groupingBy(
                        ChatToolEvent::getTurnIndex,
                        Collectors.mapping(this::toToolCallDto, Collectors.toList())));

        List<ChatMessageDto> result = new ArrayList<>();
        int assistantIndex = 0;
        String lastUserText = null;
        // Read the FULL transcript directly (chatMemory.get returns the compacted, summarized view
        // meant for the model, not for rendering history).
        for (Message m : chatMemoryRepository.findByConversationId(id)) {
            if (m.getMessageType() == MessageType.USER) {
                String text = m.getText();
                if (text != null && text.equals(lastUserText)) {
                    continue;
                }
                lastUserText = text;
                result.add(new ChatMessageDto("user", text));
            } else if (m.getMessageType() == MessageType.ASSISTANT) {
                lastUserText = null;
                List<ToolCallDto> tools = toolsByTurn.get(assistantIndex);
                result.add(new ChatMessageDto("assistant", m.getText(), tools));
                assistantIndex++;
            }
        }
        return result;
    }

    @Override
    public List<SupervisorSessionSummaryDto> listAllForSupervisor(int limit) {
        int cap = limit <= 0 ? 100 : Math.min(limit, 500);
        return repository.findMostRecentAcrossUsers(cap).stream()
                .map(s -> new SupervisorSessionSummaryDto(
                        s.id(), s.userId(), s.title(), s.updatedAt(), s.archived(), s.temporary()))
                .toList();
    }

    @Override
    public long assistantMessageCount(String id) {
        // SQL COUNT against the Spring AI memory table — loading and deserializing the whole
        // transcript just to count assistant rows was a per-turn tax on long sessions.
        return chatTranscriptRepository.countByType(id, MessageType.ASSISTANT.name());
    }

    private ToolCallDto toToolCallDto(ChatToolEvent e) {
        return new ToolCallDto(e.getCallId(), e.getToolName(), e.getToolInput(),
                e.getToolOutput(), e.isError());
    }

    @Override
    public ChatSessionDto update(String id, UpdateSessionRequest request) {
        String userId = securityContextService.currentUserIdOrThrow();
        requireSession(id, userId);
        long now = Instant.now().getEpochSecond();
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            repository.updateTitle(id, request.getTitle().trim(), now, userId);
        }
        if (request.getArchived() != null) {
            repository.updateArchived(id, request.getArchived(), now, userId);
        }
        if (request.getStyleId() != null) {
            // Blank clears the pinned style (FK is nullable); a value pins it.
            repository.updateStyle(id, request.getStyleId().isBlank() ? null : request.getStyleId(), now, userId);
        }
        if (request.getProviderId() != null) {
            // Blank clears the pinned provider (falls back to the platform default).
            repository.updateProvider(id, request.getProviderId().isBlank() ? null : request.getProviderId(), now, userId);
        }
        return repository.findById(id, userId).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
    }

    @Override
    public void touchAndMaybeTitle(String id, String firstMessage, String requestId, String lang) {
        String userId = securityContextService.currentUserIdOrThrow();
        ChatSession session = repository.findById(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
        long now = Instant.now().getEpochSecond();
        // The touch is sync (cheap UPDATE); the title LLM call is fire-and-forget so the first
        // message of a session never waits a model round-trip before streaming starts. The client
        // picks up the generated title from its session-list refresh at turn end.
        repository.touch(id, now, userId);
        if (DEFAULT_TITLE.equals(session.getTitle()) && firstMessage != null && !firstMessage.isBlank()) {
            LlmUsageContext ctx = new LlmUsageContext(requestId, id, session.getAssistantId(), userId);
            sessionTitleService.generateAndSetTitleAsync(id, userId, firstMessage, lang, ctx);
        }
    }

    @Override
    public void truncateFrom(String id, int messageIndex) {
        String userId = securityContextService.currentUserIdOrThrow();
        requireSession(id, userId);

        // Keep only USER/ASSISTANT messages, in render order, so messageIndex (the frontend's
        // flat render index) maps 1:1 onto chat memory. Everything from messageIndex onward is
        // dropped; the client then re-runs the turn from the (possibly edited) message.
        List<Message> ordered = chatMemoryRepository.findByConversationId(id).stream()
                .filter(m -> m.getMessageType() == MessageType.USER
                        || m.getMessageType() == MessageType.ASSISTANT)
                .toList();
        int cut = Math.max(0, Math.min(messageIndex, ordered.size()));
        List<Message> kept = new ArrayList<>(ordered.subList(0, cut));
        int assistantKept = (int) kept.stream()
                .filter(m -> m.getMessageType() == MessageType.ASSISTANT)
                .count();

        // Spring AI's ChatMemory has no partial delete, so clear and replay the kept prefix
        // (same saveAll path used in normal operation, so ordering is preserved).
        chatMemory.clear(id);
        if (!kept.isEmpty()) {
            chatMemory.add(id, kept);
        }
        // Drop persisted tool events for the dropped turns; turn_index is the assistant-message index.
        toolEventRepository.deleteFromTurn(id, assistantKept);
        repository.touch(id, Instant.now().getEpochSecond(), userId);
        log.info("Truncated session {} from message index {} (kept {} assistant turn(s))",
                id, cut, assistantKept);
    }

    @Override
    public void delete(String id) {
        String userId = securityContextService.currentUserIdOrThrow();
        requireSession(id, userId);
        repository.delete(id, userId);
        chatMemory.clear(id);
        log.info("Deleted chat session {} for user {}", id, userId);
    }

    @Override
    public String resolveAssistantId(String sessionId) {
        String userId = securityContextService.currentUserIdOrThrow();
        return repository.findById(sessionId, userId)
                .map(ChatSession::getAssistantId)
                .filter(a -> a != null && !a.isBlank())
                .orElseGet(assistantService::defaultAssistantId);
    }

    @Override
    public boolean isTemporary(String sessionId) {
        // Resolved without the per-user filter (findOwner) so it works on the streaming thread.
        return repository.findOwner(sessionId)
                .map(ChatSessionRepository.SessionOwner::temporary)
                .orElse(false);
    }

    @Override
    public int purgeExpiredTemporary(int retentionDays) {
        long cutoff = Instant.now().getEpochSecond() - (long) retentionDays * 86_400L;
        List<String> expired = repository.findExpiredTemporary(cutoff);
        for (String id : expired) {
            try {
                repository.deleteById(id);   // no per-user filter — this is a system job
                chatMemory.clear(id);
            } catch (RuntimeException e) {
                log.warn("Failed to purge expired temporary chat {}: {}", id, e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("Purged {} expired temporary chat(s) (retention {} days)", expired.size(), retentionDays);
        }
        return expired.size();
    }

    @Override
    public String resolveStyleId(String sessionId) {
        String userId = securityContextService.currentUserIdOrThrow();
        ChatSession session = repository.findById(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (session.getStyleId() != null && !session.getStyleId().isBlank()) {
            return session.getStyleId();
        }
        String assistantId = session.getAssistantId();
        if (assistantId == null || assistantId.isBlank()) {
            assistantId = assistantService.defaultAssistantId();
        }
        return responseStyleService.defaultStyleIdForAssistant(assistantId);
    }

    /** Session-pinned LLM provider, or null when the platform default should be used. */
    @Override
    public String resolveProviderId(String sessionId) {
        String userId = securityContextService.currentUserIdOrThrow();
        ChatSession session = repository.findById(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        return session.getProviderId();
    }

    private void requireSession(String id, String userId) {
        if (repository.findById(id, userId).isEmpty()) {
            throw new ResourceNotFoundException("Session not found: " + id);
        }
    }

    private ChatSessionDto toDto(ChatSession s) {
        return ChatSessionDto.builder()
                .id(s.getId())
                .title(s.getTitle())
                .archived(s.isArchived())
                .assistantId(s.getAssistantId())
                .styleId(s.getStyleId())
                .providerId(s.getProviderId())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .temporary(s.isTemporary())
                .build();
    }
}
