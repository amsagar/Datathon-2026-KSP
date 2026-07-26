package com.ksp.agent.suggestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksp.agent.assistant.entity.Assistant;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.chat.dto.response.ChatSessionDto;
import com.ksp.agent.chat.service.ChatSessionService;
import com.ksp.agent.memory.dto.response.SemanticFactDto;
import com.ksp.agent.memory.service.SemanticMemoryService;
import com.ksp.agent.suggestion.repo.PromptSuggestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * Builds the rotating starter-prompt suggestions shown on the empty chat screen.
 *
 * <p>Serving is DB-only and fast: it blends personalized (user-level) suggestions ahead of
 * assistant-level ones, shuffles, and caps the pool so the client can rotate through it.
 * Generation is explicit (called in the background / from settings) and uses the LLM:
 * <ul>
 *   <li><b>assistant-level</b> — from the assistant's own name + system prompt (relevant on day one,
 *       before any user history exists);</li>
 *   <li><b>user-level</b> — from the user's long-term memories + recent chat titles, so chips become
 *       specific to what that officer actually works on.</li>
 * </ul>
 */
@Service
@Slf4j
public class PromptSuggestionService {

    private static final int MAX_POOL = 12;
    private static final int GENERATE_COUNT = 6;
    private static final int MAX_TEXT_LEN = 140;

    private final PromptSuggestionRepository repository;
    private final AssistantService assistantService;
    private final SemanticMemoryService semanticMemoryService;
    private final ChatSessionService chatSessionService;
    private final ChatClient suggestionsChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    public PromptSuggestionService(PromptSuggestionRepository repository,
                                   AssistantService assistantService,
                                   SemanticMemoryService semanticMemoryService,
                                   ChatSessionService chatSessionService,
                                   @Qualifier("suggestionsChatClient") ChatClient suggestionsChatClient) {
        this.repository = repository;
        this.assistantService = assistantService;
        this.semanticMemoryService = semanticMemoryService;
        this.chatSessionService = chatSessionService;
        this.suggestionsChatClient = suggestionsChatClient;
    }

    /** Blended, shuffled suggestion pool for the empty screen. Never throws — returns [] on failure. */
    public List<String> serve(String assistantId, String userId, String lang) {
        try {
            String normLang = normalizeLang(lang);
            List<String> personalized = userId == null ? List.of()
                    : repository.findForUser(assistantId, userId, normLang);
            List<String> assistantLevel = repository.findAssistantLevel(assistantId, normLang);
            if (personalized.isEmpty() && assistantLevel.isEmpty() && !"en".equals(normLang)) {
                assistantLevel = repository.findAssistantLevel(assistantId, "en"); // language fallback
            }
            List<String> shuffledPersonalized = new ArrayList<>(new LinkedHashSet<>(personalized));
            List<String> shuffledAssistant = new ArrayList<>(new LinkedHashSet<>(assistantLevel));
            Collections.shuffle(shuffledPersonalized, random);
            Collections.shuffle(shuffledAssistant, random);
            // Personalized first so the most relevant chips win a limited display slot.
            LinkedHashSet<String> pool = new LinkedHashSet<>(shuffledPersonalized);
            pool.addAll(shuffledAssistant);
            return pool.stream().limit(MAX_POOL).toList();
        } catch (RuntimeException e) {
            log.warn("Failed to serve prompt suggestions for assistant {}: {}", assistantId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Regenerate assistant-level suggestions from the assistant's name + system prompt, and — when a
     * user is given — also personalized suggestions from their memories + recent chats. Best-effort:
     * the seeded rows remain if generation fails, so the empty screen is never blank.
     */
    public List<String> generate(String assistantId, String userId, String lang, boolean personalized) {
        String normLang = normalizeLang(lang);
        long now = Instant.now().toEpochMilli();
        Assistant assistant;
        try {
            assistant = assistantService.requireEntity(assistantId);
        } catch (RuntimeException e) {
            log.warn("Cannot generate suggestions — assistant {} not found: {}", assistantId, e.getMessage());
            return serve(assistantId, userId, lang);
        }

        List<String> generated = callModel(assistantPrompt(assistant, normLang));
        if (!generated.isEmpty()) {
            repository.deleteAssistantGenerated(assistantId, normLang);
            for (String text : generated) {
                repository.insert(assistantId, null, text, normLang, "assistant", now);
            }
        }

        if (personalized && userId != null) {
            String context = userContext(userId);
            if (!context.isBlank()) {
                List<String> personal = callModel(personalizedPrompt(assistant, normLang, context));
                if (!personal.isEmpty()) {
                    repository.deleteUserGenerated(assistantId, userId, normLang);
                    for (String text : personal) {
                        repository.insert(assistantId, userId, text, normLang, "user", now);
                    }
                }
            }
        }
        return serve(assistantId, userId, lang);
    }

    /** True when this assistant+lang has no LLM-generated assistant-level rows yet (seeds don't count). */
    public boolean needsAssistantGeneration(String assistantId, String lang) {
        try {
            return repository.countGenerated(assistantId, normalizeLang(lang)) == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String assistantPrompt(Assistant assistant, String lang) {
        return """
                You write short example questions a user could click to start a conversation with an AI assistant.
                Assistant name: %s
                Assistant purpose (system prompt):
                %s

                Write %d distinct, specific, useful starter questions this assistant can actually answer, each
                under 12 words, phrased as a user would type them. %s
                Return ONLY a JSON array of strings, no prose, no numbering.
                """.formatted(assistant.getName(), truncate(assistant.getSystemPrompt(), 1500),
                GENERATE_COUNT, langInstruction(lang));
    }

    private String personalizedPrompt(Assistant assistant, String lang, String userContext) {
        return """
                You write short, personalized example questions to start a conversation with an AI assistant.
                Assistant name: %s
                What this user has been working on (their saved notes and recent chat topics):
                %s

                Based on the assistant's purpose and this user's context, write %d starter questions that are
                clearly relevant to THIS user, each under 12 words, phrased as they would type them. Stay within
                what the assistant can do (crime data, cases, offenders, trends). %s
                Return ONLY a JSON array of strings, no prose, no numbering.
                """.formatted(assistant.getName(), truncate(userContext, 1500),
                GENERATE_COUNT, langInstruction(lang));
    }

    private String userContext(String userId) {
        StringBuilder sb = new StringBuilder();
        try {
            List<SemanticFactDto> facts = semanticMemoryService.listForUser(userId);
            for (SemanticFactDto f : facts.stream().limit(15).toList()) {
                sb.append("- ").append(f.getSubject()).append(' ')
                        .append(f.getPredicate()).append(' ').append(f.getObject()).append('\n');
            }
        } catch (RuntimeException e) {
            log.debug("No memories for user {}: {}", userId, e.getMessage());
        }
        try {
            List<ChatSessionDto> sessions = chatSessionService.list(false);
            for (ChatSessionDto s : sessions.stream().limit(10).toList()) {
                if (s.getTitle() != null && !s.getTitle().isBlank()) {
                    sb.append("- recent chat: ").append(s.getTitle()).append('\n');
                }
            }
        } catch (RuntimeException e) {
            log.debug("No recent sessions for user {}: {}", userId, e.getMessage());
        }
        return sb.toString().trim();
    }

    /** Call the LLM and parse a JSON array of short strings. Tolerant of code fences / stray prose. */
    private List<String> callModel(String prompt) {
        try {
            String raw = suggestionsChatClient.prompt().user(prompt).call().content();
            return parseSuggestions(raw);
        } catch (RuntimeException e) {
            log.warn("Suggestion generation model call failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseSuggestions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.strip();
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        List<String> out = new ArrayList<>();
        if (start >= 0 && end > start) {
            try {
                JsonNode arr = objectMapper.readTree(cleaned.substring(start, end + 1));
                if (arr.isArray()) {
                    for (JsonNode n : arr) {
                        addClean(out, n.asText(""));
                    }
                }
            } catch (Exception ignored) {
                // fall through to line parsing
            }
        }
        if (out.isEmpty()) {
            for (String line : cleaned.split("\\r?\\n")) {
                addClean(out, line.replaceAll("^[-*\\d.\\s\"]+", "").replaceAll("[\"]+$", ""));
            }
        }
        return out.stream().limit(GENERATE_COUNT).toList();
    }

    private void addClean(List<String> out, String text) {
        if (text == null) {
            return;
        }
        String t = text.strip();
        if (t.length() > MAX_TEXT_LEN) {
            t = t.substring(0, MAX_TEXT_LEN).strip();
        }
        if (!t.isBlank() && !out.contains(t)) {
            out.add(t);
        }
    }

    private static String langInstruction(String lang) {
        return "kn".equals(lang) ? "Write the questions in Kannada (ಕನ್ನಡ)." : "Write the questions in English.";
    }

    private static String normalizeLang(String lang) {
        return "kn".equalsIgnoreCase(lang) ? "kn" : "en";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
