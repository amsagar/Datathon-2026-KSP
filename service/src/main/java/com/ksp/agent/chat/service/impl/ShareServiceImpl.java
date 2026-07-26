package com.ksp.agent.chat.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.assistant.entity.Assistant;
import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.chat.dto.response.ChatMessageDto;
import com.ksp.agent.chat.dto.response.ShareLinkDto;
import com.ksp.agent.chat.dto.response.SharedChatDto;
import com.ksp.agent.chat.entity.ChatSession;
import com.ksp.agent.chat.entity.ChatShare;
import com.ksp.agent.chat.repo.ChatSessionRepository;
import com.ksp.agent.chat.repo.ChatShareRepository;
import com.ksp.agent.chat.service.ChatSessionService;
import com.ksp.agent.chat.service.ShareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ShareServiceImpl implements ShareService {

    private final ChatSessionService chatSessionService;
    private final ChatSessionRepository sessionRepository;
    private final ChatShareRepository shareRepository;
    private final AssistantService assistantService;
    private final SecurityContextService securityContextService;
    private final ObjectMapper objectMapper;

    public ShareServiceImpl(ChatSessionService chatSessionService,
                            ChatSessionRepository sessionRepository,
                            ChatShareRepository shareRepository,
                            AssistantService assistantService,
                            SecurityContextService securityContextService,
                            ObjectMapper objectMapper) {
        this.chatSessionService = chatSessionService;
        this.sessionRepository = sessionRepository;
        this.shareRepository = shareRepository;
        this.assistantService = assistantService;
        this.securityContextService = securityContextService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ShareLinkDto createOrRefresh(String sessionId) {
        String userId = securityContextService.currentUserIdOrThrow();
        ChatSession session = requireOwnedSession(sessionId, userId);

        // Snapshot the rendered messages, stripping tool cards (text + artifacts only). Artifacts
        // live inside the assistant message `content`, so they are preserved.
        List<ChatMessageDto> stripped = chatSessionService.messages(sessionId).stream()
                .map(m -> new ChatMessageDto(m.getRole(), m.getContent()))
                .toList();

        String assistantName = null;
        if (session.getAssistantId() != null && !session.getAssistantId().isBlank()) {
            try {
                Assistant assistant = assistantService.requireEntity(session.getAssistantId());
                assistantName = assistant.getName();
            } catch (RuntimeException e) {
                log.warn("Could not resolve assistant name for share of session {}: {}",
                        sessionId, e.getMessage());
            }
        }

        long now = Instant.now().getEpochSecond();
        shareRepository.upsert(sessionId, userId, session.getTitle(), assistantName,
                writeJson(stripped), stripped.size(), now);
        return toLinkDto(shareRepository.findBySession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Share not found after upsert: " + sessionId)));
    }

    @Override
    public Optional<ShareLinkDto> getForSession(String sessionId) {
        requireOwnedSession(sessionId, securityContextService.currentUserIdOrThrow());
        return shareRepository.findBySession(sessionId).map(this::toLinkDto);
    }

    @Override
    public SharedChatDto view(String shareId) {
        // Any authenticated user may view; the SecurityConfig `authenticated()` rule gates this.
        securityContextService.currentUserIdOrThrow();
        ChatShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Shared chat not found: " + shareId));
        return SharedChatDto.builder()
                .title(share.getTitle())
                .assistantName(share.getAssistantName())
                .messages(readMessages(share.getMessagesJson()))
                .createdAt(share.getCreatedAt())
                .build();
    }

    @Override
    public void revoke(String shareId) {
        String userId = securityContextService.currentUserIdOrThrow();
        ChatShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Shared chat not found: " + shareId));
        if (!share.getCreatedBy().equals(userId)) {
            // Don't reveal existence to non-owners.
            throw new ResourceNotFoundException("Shared chat not found: " + shareId);
        }
        shareRepository.deleteById(shareId);
    }

    private ChatSession requireOwnedSession(String sessionId, String userId) {
        return sessionRepository.findById(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
    }

    private String writeJson(List<ChatMessageDto> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize shared messages", e);
        }
    }

    private List<ChatMessageDto> readMessages(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChatMessageDto>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read shared messages", e);
        }
    }

    private ShareLinkDto toLinkDto(ChatShare share) {
        return ShareLinkDto.builder()
                .shareId(share.getId())
                .messageCount(share.getMessageCount())
                .createdAt(share.getCreatedAt())
                .updatedAt(share.getUpdatedAt())
                .build();
    }
}
