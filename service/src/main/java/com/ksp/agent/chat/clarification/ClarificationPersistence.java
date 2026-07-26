package com.ksp.agent.chat.clarification;

import com.ksp.agent.chat.repo.ChatToolEventRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists {@code AskUserQuestionTool} turns in {@code chat_tool_event} so Q&amp;A survives reload.
 */
@Component
public class ClarificationPersistence {

    private final ChatToolEventRepository toolEventRepository;
    private final ObjectMapper objectMapper;

    public ClarificationPersistence(ChatToolEventRepository toolEventRepository,
                                    ObjectMapper objectMapper) {
        this.toolEventRepository = toolEventRepository;
        this.objectMapper = objectMapper;
    }

    public void saveToolCall(String sessionId, int turnIndex, int seq, String callId,
                             String toolInput, long now) {
        toolEventRepository.save(sessionId, turnIndex, seq, callId,
                com.ksp.agent.chat.tooling.AskUserQuestionToolFactory.TOOL_NAME,
                toolInput, "", false, now);
    }

    public void saveQuestionsPayload(String sessionId, String callId,
                                     List<ClarificationQuestionDto> questions) {
        if (sessionId == null || callId == null || questions == null || questions.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("questions", new ArrayList<>(questions));
            String json = objectMapper.writeValueAsString(payload);
            toolEventRepository.updateInput(sessionId, callId, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist clarification questions", e);
        }
    }

    public void saveAnswers(String sessionId, String callId, Map<String, String> answers) {
        if (sessionId == null || callId == null || answers == null) {
            return;
        }
        writeAnswersJson(sessionId, callId, answers);
    }

    /** Persists answers from the tool return payload when it contains an {@code answers} map. */
    public void saveAnswersFromToolOutput(String sessionId, String callId, String toolOutput) {
        if (sessionId == null || callId == null || toolOutput == null || toolOutput.isBlank()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(toolOutput, Map.class);
            Object answersObj = parsed.get("answers");
            if (!(answersObj instanceof Map<?, ?> answersMap) || answersMap.isEmpty()) {
                return;
            }
            Map<String, String> answers = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : answersMap.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    answers.put(e.getKey().toString(), e.getValue().toString());
                }
            }
            if (!answers.isEmpty()) {
                writeAnswersJson(sessionId, callId, answers);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist clarification answers from tool output", e);
        }
    }

    private void writeAnswersJson(String sessionId, String callId, Map<String, String> answers) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("answers", answers);
            String json = objectMapper.writeValueAsString(payload);
            toolEventRepository.updateOutput(sessionId, callId, json, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist clarification answers", e);
        }
    }
}
