package com.ksp.agent.chat.memory;

import com.ksp.agent.chat.entity.ChatSessionSummary;
import com.ksp.agent.chat.repo.ChatSessionRepository;
import com.ksp.agent.chat.repo.ChatSessionSummaryRepository;
import com.ksp.agent.chat.usage.LlmUsageContext;
import com.ksp.agent.chat.usage.LlmUsageRecorder;
import com.ksp.agent.chat.usage.LlmUsageSource;
import com.ksp.agent.memory.entity.SemanticFact;
import com.ksp.agent.memory.service.SemanticMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a running summary of aged-out conversation turns so long sessions keep their early
 * context instead of having it silently dropped by the message window. Runs off the request thread
 * ({@link Async}) so a turn never waits on the summarization model call.
 */
@Service
@Slf4j
public class ConversationSummaryService {

    private final ChatClient summaryChatClient;
    private final ChatClient factExtractionChatClient;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatSessionSummaryRepository summaryRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final SemanticMemoryService semanticMemoryService;
    private final LlmUsageRecorder llmUsageRecorder;
    private final ObjectMapper objectMapper;
    private final int recentWindow;
    private final double extractionMinConfidence;

    /** Sessions with a summarization run in flight, so concurrent turns don't summarize twice. */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public ConversationSummaryService(@Qualifier("summaryChatClient") ChatClient summaryChatClient,
                                      @Qualifier("factExtractionChatClient") ChatClient factExtractionChatClient,
                                      ChatMemoryRepository chatMemoryRepository,
                                      ChatSessionSummaryRepository summaryRepository,
                                      ChatSessionRepository chatSessionRepository,
                                      SemanticMemoryService semanticMemoryService,
                                      LlmUsageRecorder llmUsageRecorder,
                                      ObjectMapper objectMapper,
                                      @Value("${agent.memory.recent-window:20}") int recentWindow,
                                      @Value("${agent.memory.semantic.extract-min-confidence:0.5}") double extractionMinConfidence) {
        this.summaryChatClient = summaryChatClient;
        this.factExtractionChatClient = factExtractionChatClient;
        this.chatMemoryRepository = chatMemoryRepository;
        this.summaryRepository = summaryRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.semanticMemoryService = semanticMemoryService;
        this.llmUsageRecorder = llmUsageRecorder;
        this.objectMapper = objectMapper;
        this.recentWindow = recentWindow;
        this.extractionMinConfidence = extractionMinConfidence;
    }

    @Async
    public void summarizeAsync(LlmUsageContext usageContext) {
        if (usageContext == null) {
            return;
        }
        String sessionId = usageContext.sessionId();
        if (!inFlight.add(sessionId)) {
            return; // a run is already in progress for this session
        }
        try {
            List<Message> transcript = chatMemoryRepository.findByConversationId(sessionId);
            int size = transcript.size();

            ChatSessionSummary existing = summaryRepository.findBySession(sessionId).orElse(null);
            String prior = existing != null ? existing.getSummary() : "";
            int summarizedThrough = existing != null ? existing.getSummarizedThroughCount() : 0;
            int from = Math.max(0, Math.min(summarizedThrough, size));

            // Keep the last `recentWindow` messages verbatim; fold everything older into the summary.
            int target = size - recentWindow;
            if (target <= from) {
                return; // nothing new has aged out since the last summary
            }

            List<Message> slice = transcript.subList(from, target);
            String userContent = "PRIOR SUMMARY:\n" + (prior.isBlank() ? "(none)" : prior)
                    + "\n\nADDITIONAL MESSAGES:\n" + render(slice);

            ChatResponse response = summaryChatClient.prompt().user(userContent).call().chatResponse();
            llmUsageRecorder.recordFromResponse(usageContext, LlmUsageSource.summary, response);
            String newSummary = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText()
                    : null;
            if (newSummary == null || newSummary.isBlank()) {
                log.warn("Summarizer returned empty content for session {}; keeping prior summary", sessionId);
                return;
            }
            summaryRepository.upsert(sessionId, newSummary.strip(), target, Instant.now().getEpochSecond());
            log.info("Summarized session {} through message {} (kept last {} verbatim)",
                    sessionId, target, recentWindow);

            // Consolidation: mine durable facts from the same aged-out slice into long-term semantic
            // memory. Best-effort — a failure here never affects summarization.
            extractFacts(sessionId, slice, usageContext);
        } catch (Exception e) {
            log.warn("Conversation summarization failed for session {}: {}", sessionId, e.getMessage());
        } finally {
            inFlight.remove(sessionId);
        }
    }

    /**
     * Extract durable subject-predicate-object facts from a slice of aged-out turns and store them in
     * long-term semantic memory, scoped to the session's owner and assistant. Runs on the async
     * summarization thread (no SecurityContext), so ownership is resolved from the session row.
     */
    private void extractFacts(String sessionId, List<Message> slice, LlmUsageContext usageContext) {
        if (slice == null || slice.isEmpty()) {
            return;
        }
        ChatSessionRepository.SessionOwner owner = chatSessionRepository.findOwner(sessionId).orElse(null);
        if (owner == null || owner.userId() == null || owner.userId().isBlank()) {
            return; // can't attribute the facts to anyone
        }
        if (owner.temporary()) {
            // Temporary chats are isolated from long-term memory — never mine durable facts from them
            // (the transcript summary still updates; that's episodic and part of the persisted chat).
            return;
        }
        try {
            ChatResponse response = factExtractionChatClient.prompt().user(render(slice)).call().chatResponse();
            llmUsageRecorder.recordFromResponse(usageContext, LlmUsageSource.consolidation, response);
            String json = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText() : null;
            List<Map<String, Object>> facts = parseFacts(json);
            int kept = 0;
            for (Map<String, Object> f : facts) {
                String subject = str(f.get("subject"));
                String predicate = str(f.get("predicate"));
                String object = str(f.get("object"));
                double confidence = num(f.get("confidence"), 0.7);
                if (subject == null || predicate == null || object == null
                        || confidence < extractionMinConfidence) {
                    continue;
                }
                semanticMemoryService.remember(SemanticFact.builder()
                        .userId(owner.userId())
                        .assistantId(owner.assistantId())
                        .sessionId(sessionId)
                        .subject(subject)
                        .predicate(predicate)
                        .object(object)
                        .confidence((float) confidence)
                        .importance(1.0f)
                        .build());
                kept++;
            }
            if (kept > 0) {
                log.info("Consolidated {} long-term fact(s) from session {}", kept, sessionId);
            }
        } catch (Exception e) {
            log.warn("Fact extraction failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFacts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        // The model is told to emit a bare JSON array, but strip a stray ```json fence just in case.
        String json = raw.strip();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            if (firstNewline >= 0) {
                json = json.substring(firstNewline + 1);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.strip();
        }
        if (!json.startsWith("[")) {
            return List.of();
        }
        try {
            List<Object> parsed = objectMapper.readValue(json, List.class);
            return parsed.stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> (Map<String, Object>) o)
                    .toList();
        } catch (Exception e) {
            log.warn("Could not parse extracted facts JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).strip();
        return s.isBlank() ? null : s;
    }

    private static double num(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value).strip());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private static String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String role = m.getMessageType() == MessageType.USER ? "USER" : "ASSISTANT";
            sb.append(role).append(": ").append(m.getText() == null ? "" : m.getText()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
