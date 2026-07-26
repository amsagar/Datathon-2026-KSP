package com.ksp.agent.chat.service.impl;

import com.ksp.agent.chat.repo.ChatSessionRepository;
import com.ksp.agent.chat.usage.LlmUsageContext;
import com.ksp.agent.chat.usage.LlmUsageRecorder;
import com.ksp.agent.chat.usage.LlmUsageSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Generates a session title from the first user message OFF the turn path. The title LLM call used
 * to run synchronously inside {@code touchAndMaybeTitle}, delaying the first token of every new
 * session by a full model round-trip; now the session touch stays sync and only this fire-and-forget
 * call + title UPDATE run on the async executor (same pattern as
 * {@link com.ksp.agent.chat.memory.ConversationSummaryService#summarizeAsync}). The client picks
 * the new title up from its session-list refresh at turn end.
 *
 * <p>Runs without a SecurityContext, so the owning user id is captured on the request thread and
 * passed in explicitly (both for the per-user UPDATE and for usage recording).
 */
@Service
@Slf4j
public class SessionTitleService {

    private static final int MAX_TITLE_CHARS = 60;

    private final ChatClient titleChatClient;
    private final ChatSessionRepository repository;
    private final LlmUsageRecorder llmUsageRecorder;

    public SessionTitleService(@Qualifier("titleChatClient") ChatClient titleChatClient,
                               ChatSessionRepository repository,
                               LlmUsageRecorder llmUsageRecorder) {
        this.titleChatClient = titleChatClient;
        this.repository = repository;
        this.llmUsageRecorder = llmUsageRecorder;
    }

    @Async
    public void generateAndSetTitleAsync(String sessionId, String userId, String firstMessage,
                                         String lang, LlmUsageContext usageContext) {
        String title = generateTitle(firstMessage, lang, usageContext, userId);
        try {
            repository.updateTitle(sessionId, title, Instant.now().getEpochSecond(), userId);
        } catch (RuntimeException e) {
            log.warn("Failed to persist generated title for session {}: {}", sessionId, e.getMessage());
        }
    }

    private String generateTitle(String firstMessage, String lang, LlmUsageContext usageCtx, String userId) {
        String titleLang = "kn".equalsIgnoreCase(lang) ? "Kannada" : "English";
        try {
            ChatResponse response = titleChatClient.prompt()
                    .user("Title language: " + titleLang + "\n\n" + firstMessage)
                    .call()
                    .chatResponse();
            llmUsageRecorder.recordFromResponse(usageCtx, LlmUsageSource.title, response, userId);
            String title = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText()
                    : null;
            if (title != null && !title.isBlank()) {
                return truncate(title.strip().replaceAll("^[\"']|[\"']$", ""));
            }
        } catch (Exception e) {
            log.warn("Title generation failed, falling back to truncation: {}", e.getMessage());
        }
        return truncate(firstMessage.strip());
    }

    private static String truncate(String text) {
        return text.length() > MAX_TITLE_CHARS ? text.substring(0, MAX_TITLE_CHARS).strip() : text;
    }
}
