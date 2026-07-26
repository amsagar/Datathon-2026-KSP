package com.ksp.agent.chat.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.util.context.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured audit logging for chat turns and tool execution. Uses SLF4J MDC so all log lines
 * in a turn share {@code sessionId}, {@code requestId}, {@code assistantId}, and {@code turnIndex}.
 */
public final class ChatAuditLog {

    private static final Logger log = LoggerFactory.getLogger(ChatAuditLog.class);

    public static final String MDC_SESSION_ID = "sessionId";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_ASSISTANT_ID = "assistantId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_TURN_INDEX = "turnIndex";

    private static final int LOG_PREVIEW_CHARS = 500;
    private static final int COMMAND_PREVIEW_CHARS = 200;

    private ChatAuditLog() {
    }

    public static void putContext(String sessionId, String requestId, String assistantId, int turnIndex) {
        putContext(sessionId, requestId, assistantId, null, turnIndex);
    }

    public static void putContext(String sessionId, String requestId, String assistantId,
                                  String userId, int turnIndex) {
        if (sessionId != null) {
            MDC.put(MDC_SESSION_ID, sessionId);
        }
        if (requestId != null) {
            MDC.put(MDC_REQUEST_ID, requestId);
        }
        if (assistantId != null) {
            MDC.put(MDC_ASSISTANT_ID, assistantId);
        }
        if (userId != null) {
            MDC.put(MDC_USER_ID, userId);
        }
        MDC.put(MDC_TURN_INDEX, String.valueOf(turnIndex));
    }

    public static void clearContext() {
        MDC.remove(MDC_SESSION_ID);
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_ASSISTANT_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_TURN_INDEX);
    }

    public static String requestIdFromMdc() {
        return MDC.get(MDC_REQUEST_ID);
    }

    public static String sessionIdFromMdc() {
        return MDC.get(MDC_SESSION_ID);
    }

    public static String assistantIdFromMdc() {
        return MDC.get(MDC_ASSISTANT_ID);
    }

    public static String userIdFromMdc() {
        return MDC.get(MDC_USER_ID);
    }

    /**
     * Wraps a reactive stream so MDC is set for the subscription thread and cleared on termination.
     */
    public static <T> Flux<T> runWithContext(
            String sessionId,
            String requestId,
            String assistantId,
            int turnIndex,
            Flux<T> flux) {
        return runWithContext(sessionId, requestId, assistantId, null, turnIndex, flux);
    }

    public static <T> Flux<T> runWithContext(
            String sessionId,
            String requestId,
            String assistantId,
            String userId,
            int turnIndex,
            Flux<T> flux) {
        return flux.contextWrite(Context.of(
                        MDC_SESSION_ID, nullToEmpty(sessionId),
                        MDC_REQUEST_ID, nullToEmpty(requestId),
                        MDC_ASSISTANT_ID, nullToEmpty(assistantId),
                        MDC_USER_ID, nullToEmpty(userId),
                        MDC_TURN_INDEX, String.valueOf(turnIndex)))
                .doOnEach(signal -> {
                    if (signal.isOnSubscribe()) {
                        putContext(sessionId, requestId, assistantId, userId, turnIndex);
                    }
                })
                .doFinally(signalType -> clearContext());
    }

    public static void turnStart(Map<String, ?> fields) {
        log.info(formatEvent("turn_start", fields));
    }

    /**
     * Logs the exact tool definitions passed to the model for this turn (after search-mode gating
     * and skill-tool merge). When {@code searchMode} is true, also logs tools stashed in the search
     * catalog (reachable only via {@code search_tools} / {@code invoke_tool}).
     */
    public static void toolsSentToModel(
            boolean searchMode,
            List<String> toolsSentToModel,
            List<String> searchCatalogTools,
            List<String> skillToolsSkippedDuplicate) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("searchMode", searchMode);
        fields.put("toolCount", toolsSentToModel.size());
        fields.put("toolsSentToModel", String.join(",", toolsSentToModel));
        fields.put("toolsIndexed", indexToolNames(toolsSentToModel));
        if (searchMode && searchCatalogTools != null && !searchCatalogTools.isEmpty()) {
            fields.put("searchCatalogCount", searchCatalogTools.size());
            fields.put("searchCatalogTools", String.join(",", searchCatalogTools));
            fields.put("searchCatalogIndexed", indexToolNames(searchCatalogTools));
        }
        if (skillToolsSkippedDuplicate != null && !skillToolsSkippedDuplicate.isEmpty()) {
            fields.put("skillToolsSkippedDuplicate", String.join(",", skillToolsSkippedDuplicate));
        }
        log.info(formatEvent("tools_sent_to_model", fields));
    }

    private static String indexToolNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(i + 1).append(':').append(names.get(i));
        }
        return sb.toString();
    }

    public static void turnEnd(Map<String, ?> fields) {
        log.info(formatEvent("turn_end", fields));
    }

    public static void toolStart(String toolName, String callId, int inputLen, String commandPreview) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("toolName", toolName);
        fields.put("callId", callId);
        fields.put("inputLen", inputLen);
        if (commandPreview != null && !commandPreview.isBlank()) {
            fields.put("commandPreview", truncate(commandPreview, COMMAND_PREVIEW_CHARS));
        }
        log.info(formatEvent("tool_start", fields));
    }

    public static void toolEnd(String toolName, String callId, long durationMs, int outputLen, boolean error) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("toolName", toolName);
        fields.put("callId", callId);
        fields.put("durationMs", durationMs);
        fields.put("outputLen", outputLen);
        fields.put("error", error);
        log.info(formatEvent("tool_end", fields));
    }

    public static void toolVerbose(String toolName, String callId, String input, String output, boolean error) {
        if (!log.isDebugEnabled()) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("toolName", toolName);
        fields.put("callId", callId);
        fields.put("error", error);
        if (input != null) {
            fields.put("inputPreview", truncate(input, LOG_PREVIEW_CHARS));
        }
        if (output != null) {
            fields.put("outputPreview", truncate(output, LOG_PREVIEW_CHARS));
        }
        log.debug(formatEvent("tool_verbose", fields));
    }

    public static void httpToolOk(String toolName, String method, String url, long durationMs, int responseBytes) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("toolName", toolName);
        fields.put("method", method);
        fields.put("url", sanitizeUrl(url));
        fields.put("durationMs", durationMs);
        fields.put("responseBytes", responseBytes);
        log.info(formatEvent("http_tool_ok", fields));
    }

    public static void searchTools(String query, java.util.List<String> toolNames) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("query", truncate(query, LOG_PREVIEW_CHARS));
        fields.put("resultCount", toolNames.size());
        fields.put("toolNames", String.join(",", toolNames));
        log.info(formatEvent("search_tools", fields));
    }

    public static void invokeToolDelegate(String targetName) {
        log.info(formatEvent("invoke_tool", Map.of("targetTool", targetName)));
    }

    public static void skillMaterialized(String assistantId, int skillCount, String workspacePath,
                                         java.util.List<String> skillNames) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("assistantId", assistantId);
        fields.put("skillCount", skillCount);
        fields.put("workspacePath", workspacePath);
        if (skillNames != null && !skillNames.isEmpty()) {
            fields.put("skillNames", String.join(",", skillNames));
        }
        log.info(formatEvent("skill_materialized", fields));
    }

    public static boolean isShellLikeTool(String toolName) {
        if (toolName == null) {
            return false;
        }
        String lower = toolName.toLowerCase();
        return "bash".equals(lower) || "shell".equals(lower) || lower.contains("shell");
    }

    public static String shellCommandPreview(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return null;
        }
        return truncate(toolInput.trim(), COMMAND_PREVIEW_CHARS);
    }

    private static String formatEvent(String event, Map<String, ?> fields) {
        StringBuilder sb = new StringBuilder("event=").append(event);
        for (Map.Entry<String, ?> e : fields.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            sb.append(' ').append(e.getKey()).append('=').append(escapeValue(String.valueOf(e.getValue())));
        }
        String requestId = MDC.get(MDC_REQUEST_ID);
        if (requestId != null && !fields.containsKey("requestId")) {
            sb.append(" requestId=").append(escapeValue(requestId));
        }
        return sb.toString();
    }

    private static String escapeValue(String value) {
        if (value.contains(" ") || value.contains("=")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "…";
    }

    static String sanitizeUrl(String url) {
        if (url == null) {
            return "";
        }
        // Strip query string that may contain tokens
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) + "?…" : url;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String signalTypeName(SignalType signalType) {
        return signalType == null ? "unknown" : signalType.name();
    }
}
