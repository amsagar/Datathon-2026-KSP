package com.ksp.agent.chat.usage;

import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.chat.service.LlmUsageService;
import com.ksp.agent.llm.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Records token usage for every LLM round-trip (main chat stream, title, scope guard, memory summary).
 * All calls run on the single in-house model, so the model name is resolved from configuration
 * ({@link LlmProperties}) when a caller does not pass one explicitly.
 */
@Component
@Slf4j
public class LlmUsageRecorder {

    private final LlmUsageService llmUsageService;
    private final SecurityContextService securityContextService;
    private final LlmProperties llmProperties;

    public LlmUsageRecorder(LlmUsageService llmUsageService,
                            SecurityContextService securityContextService,
                            LlmProperties llmProperties) {
        this.llmUsageService = llmUsageService;
        this.securityContextService = securityContextService;
        this.llmProperties = llmProperties;
    }

    public void record(LlmUsageContext ctx, LlmUsageSource source, Usage usage) {
        record(ctx, source, null, usage);
    }

    public void record(LlmUsageContext ctx, LlmUsageSource source, String modelName, Usage usage) {
        record(ctx, source, modelName, usage, null);
    }

    /**
     * Variant for callers off the request thread (e.g. {@code @Async} title generation), where the
     * SecurityContext is empty: the acting user id is passed explicitly instead of resolved from the
     * security context.
     */
    public void record(LlmUsageContext ctx, LlmUsageSource source, String modelName, Usage usage,
                       String explicitUserId) {
        if (ctx == null || usage == null) {
            return;
        }
        LlmUsageKind kind = source == LlmUsageSource.main ? LlmUsageKind.chat : LlmUsageKind.system;
        try {
            // Resolution order: explicit caller override → userId captured on the context at
            // request time → SecurityContext (only present on the original request thread).
            String userId = explicitUserId != null && !explicitUserId.isBlank()
                    ? explicitUserId
                    : ctx.userId() != null && !ctx.userId().isBlank()
                            ? ctx.userId()
                            : securityContextService.currentUserIdOrThrow();
            String resolvedModel = modelName == null || modelName.isBlank()
                    ? defaultModelName()
                    : modelName;
            llmUsageService.record(
                    ctx.requestId(),
                    ctx.sessionId(),
                    userId,
                    ctx.assistantId(),
                    kind,
                    source.name(),
                    resolvedModel,
                    usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                    usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                    usage.getTotalTokens() == null ? 0 : usage.getTotalTokens());
        } catch (Exception e) {
            log.warn("Failed to record LLM usage ({}): {}", source, e.getMessage());
        }
    }

    public void recordFromResponse(LlmUsageContext ctx, LlmUsageSource source, ChatResponse response) {
        recordFromResponse(ctx, source, response, null);
    }

    /** See {@link #record(LlmUsageContext, LlmUsageSource, String, Usage, String)}. */
    public void recordFromResponse(LlmUsageContext ctx, LlmUsageSource source, ChatResponse response,
                                   String explicitUserId) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        record(ctx, source, null, response.getMetadata().getUsage(), explicitUserId);
    }

    private String defaultModelName() {
        String model = llmProperties.getModel();
        return model == null || model.isBlank() ? "unknown" : model;
    }
}
