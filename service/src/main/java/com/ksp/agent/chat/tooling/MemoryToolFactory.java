package com.ksp.agent.chat.tooling;

import com.ksp.agent.memory.entity.SemanticFact;
import com.ksp.agent.memory.repo.SemanticFactRepository.ScoredFact;
import com.ksp.agent.memory.service.SemanticMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the long-term-memory tools the model can actively call during a turn:
 * {@code remember_fact} (write) and {@code recall_memory} (read). Mirrors
 * {@link AskUserQuestionToolFactory}: a per-request factory so each tool closes over the current
 * user/assistant/session captured on the request thread (where the SecurityContext is valid) rather
 * than reading identity from {@link ToolContext} at execution time, which runs on Spring AI's
 * streaming threads with an empty SecurityContext.
 */
@Component
public class MemoryToolFactory {

    private static final Logger log = LoggerFactory.getLogger(MemoryToolFactory.class);

    public static final String REMEMBER_TOOL_NAME = "remember_fact";
    public static final String RECALL_TOOL_NAME = "recall_memory";

    private static final String REMEMBER_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "subject": { "type": "string", "description": "Who or what the fact is about, usually 'user'." },
                "predicate": { "type": "string", "description": "The relationship or attribute, e.g. 'prefers_date_format'." },
                "object": { "type": "string", "description": "The value, e.g. 'DD-MM-YYYY'." },
                "confidence": { "type": "number", "description": "0..1 confidence the fact is correct. Default 0.9 for things the user states explicitly." }
              },
              "required": ["subject", "predicate", "object"]
            }
            """;

    private static final String RECALL_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "What to look up about the user." }
              },
              "required": ["query"]
            }
            """;

    private final SemanticMemoryService semanticMemoryService;
    private final ObjectMapper objectMapper;

    public MemoryToolFactory(SemanticMemoryService semanticMemoryService, ObjectMapper objectMapper) {
        this.semanticMemoryService = semanticMemoryService;
        this.objectMapper = objectMapper;
    }

    public static boolean isMemoryTool(String name) {
        return REMEMBER_TOOL_NAME.equals(name) || RECALL_TOOL_NAME.equals(name);
    }

    /**
     * Build the two memory tools for one chat turn. Identity is captured here (request thread) so the
     * tools work when invoked later on streaming threads.
     */
    public List<ToolCallback> callbacks(String userId, String assistantId, String sessionId,
                                        int recallTopK, double recallMinConfidence) {
        return List.of(
                new RememberFactCallback(userId, assistantId, sessionId),
                new RecallMemoryCallback(userId, assistantId, recallTopK, recallMinConfidence));
    }

    /** Tool: write a durable fact to long-term memory. */
    private final class RememberFactCallback implements ToolCallback {
        private final String userId;
        private final String assistantId;
        private final String sessionId;

        private RememberFactCallback(String userId, String assistantId, String sessionId) {
            this.userId = userId;
            this.assistantId = assistantId;
            this.sessionId = sessionId;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(REMEMBER_TOOL_NAME)
                    .description("Store a durable fact about the user so you can use it in future "
                            + "conversations. Call this whenever the user shares a stable preference, "
                            + "personal detail, or instruction worth keeping, or explicitly asks you to "
                            + "remember something. After storing, briefly confirm what you saved.")
                    .inputSchema(REMEMBER_INPUT_SCHEMA)
                    .build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String subject;
            String predicate;
            String object;
            double confidence = 0.9;
            try {
                Map<?, ?> in = objectMapper.readValue(toolInput, Map.class);
                subject = str(in.get("subject"));
                predicate = str(in.get("predicate"));
                object = str(in.get("object"));
                if (in.get("confidence") instanceof Number n) {
                    confidence = n.doubleValue();
                }
            } catch (Exception e) {
                return "Could not store memory: the input was not valid JSON with subject/predicate/object.";
            }
            if (subject == null || predicate == null || object == null) {
                return "Could not store memory: subject, predicate and object are all required.";
            }
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            try {
                semanticMemoryService.remember(SemanticFact.builder()
                        .userId(userId)
                        .assistantId(assistantId)
                        .sessionId(sessionId)
                        .subject(subject)
                        .predicate(predicate)
                        .object(object)
                        .confidence((float) confidence)
                        .importance(1.0f)
                        .build());
                return "Remembered: " + subject + " " + predicate.replace('_', ' ') + " " + object;
            } catch (RuntimeException e) {
                log.warn("remember_fact failed for user {}: {}", userId, e.getMessage());
                return "Could not store that memory right now.";
            }
        }
    }

    /** Tool: look up durable facts about the user by a natural-language query. */
    private final class RecallMemoryCallback implements ToolCallback {
        private final String userId;
        private final String assistantId;
        private final int topK;
        private final double minConfidence;

        private RecallMemoryCallback(String userId, String assistantId, int topK, double minConfidence) {
            this.userId = userId;
            this.assistantId = assistantId;
            this.topK = topK;
            this.minConfidence = minConfidence;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(RECALL_TOOL_NAME)
                    .description("Look up durable facts you previously stored about the user, by a "
                            + "natural-language query. Use when an answer may depend on the user's stored "
                            + "preferences or earlier-shared details that are not already shown to you.")
                    .inputSchema(RECALL_INPUT_SCHEMA)
                    .build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String query = toolInput;
            try {
                Map<?, ?> in = objectMapper.readValue(toolInput, Map.class);
                Object q = in.get("query");
                if (q != null) {
                    query = String.valueOf(q);
                }
            } catch (Exception e) {
                // Not JSON — treat the raw input as the query (same posture as search_tools).
            }
            try {
                List<ScoredFact> facts =
                        semanticMemoryService.recall(userId, assistantId, query, topK, minConfidence);
                List<Map<String, Object>> out = new ArrayList<>();
                for (ScoredFact f : facts) {
                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    entry.put("fact", f.render());
                    entry.put("confidence", f.confidence());
                    out.add(entry);
                }
                return objectMapper.writeValueAsString(Map.of("facts", out));
            } catch (RuntimeException e) {
                log.warn("recall_memory failed for user {}: {}", userId, e.getMessage());
                return "{\"facts\":[]}";
            }
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).strip();
        return s.isBlank() ? null : s;
    }
}
