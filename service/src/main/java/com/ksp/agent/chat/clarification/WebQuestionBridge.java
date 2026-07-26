package com.ksp.agent.chat.clarification;

import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Bridges the synchronous {@link AskUserQuestionTool} {@code QuestionHandler} to async web UI:
 * emits structured questions, blocks on a {@link CompletableFuture}, and resumes when the client
 * POSTs answers.
 */
@Component
public class WebQuestionBridge {

    private record PendingMeta(String sessionId, int turnIndex, String callId) {}

    private final ClarificationPersistence clarificationPersistence;
    private final ConcurrentHashMap<String, CompletableFuture<Map<String, String>>> pending =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingMeta> pendingMeta = new ConcurrentHashMap<>();

    @Value("${agent.clarification.timeout-minutes:5}")
    private int timeoutMinutes;

    public WebQuestionBridge(ClarificationPersistence clarificationPersistence) {
        this.clarificationPersistence = clarificationPersistence;
    }

    public void registerToolCall(String requestId, String sessionId, int turnIndex, String callId) {
        if (requestId != null && sessionId != null && callId != null) {
            pendingMeta.put(requestId, new PendingMeta(sessionId, turnIndex, callId));
        }
    }

    public Map<String, String> awaitAnswers(String requestId,
                                            List<AskUserQuestionTool.Question> questions,
                                            Consumer<ClarificationEventDto> emitClarification) {
        CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
        CompletableFuture<Map<String, String>> previous = pending.put(requestId, future);
        if (previous != null) {
            previous.completeExceptionally(new CancellationException("Superseded by new clarification"));
        }
        try {
            PendingMeta meta = pendingMeta.get(requestId);
            if (meta != null) {
                clarificationPersistence.saveQuestionsPayload(
                        meta.sessionId(), meta.callId(), toEvent(requestId, meta.callId(), questions).questions());
            }
            emitClarification.accept(toEvent(requestId,
                    meta != null ? meta.callId() : requestId, questions));
            return future.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new RuntimeException(
                    "Timed out after " + timeoutMinutes + " minutes waiting for user clarification", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Clarification interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Clarification failed", cause);
        } finally {
            pending.remove(requestId);
            pendingMeta.remove(requestId);
        }
    }

    public boolean submitAnswers(String requestId, Map<String, String> answers) {
        if (requestId == null || requestId.isBlank() || answers == null) {
            return false;
        }
        CompletableFuture<Map<String, String>> future = pending.get(requestId);
        if (future == null) {
            return false;
        }
        PendingMeta meta = pendingMeta.get(requestId);
        if (meta != null) {
            clarificationPersistence.saveAnswers(meta.sessionId(), meta.callId(), answers);
        }
        return future.complete(answers);
    }

    public void cancel(String requestId) {
        CompletableFuture<Map<String, String>> future = pending.remove(requestId);
        pendingMeta.remove(requestId);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new CancellationException("Chat stream ended"));
        }
    }

    static ClarificationEventDto toEvent(String requestId, String callId,
                                         List<AskUserQuestionTool.Question> questions) {
        List<ClarificationQuestionDto> dtos = new ArrayList<>();
        for (AskUserQuestionTool.Question q : questions) {
            List<ClarificationOptionDto> options = q.options().stream()
                    .map(o -> new ClarificationOptionDto(o.label(), o.description()))
                    .toList();
            boolean multi = Boolean.TRUE.equals(q.multiSelect());
            dtos.add(new ClarificationQuestionDto(q.question(), q.header(), multi, options));
        }
        return new ClarificationEventDto(requestId, callId, dtos);
    }
}
