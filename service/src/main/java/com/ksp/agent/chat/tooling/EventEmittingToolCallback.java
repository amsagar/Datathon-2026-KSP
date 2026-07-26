package com.ksp.agent.chat.tooling;

import com.ksp.agent.chat.audit.ChatAuditLog;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.UUID;

/**
 * Wraps a delegate {@link ToolCallback} and publishes a tool-call event (name + input)
 * before execution and a tool-result event (output) after, to the {@link ToolEventSink}
 * registered for the current request (resolved from {@link ToolContext}).
 */
public class EventEmittingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolEventRegistry registry;

    public EventEmittingToolCallback(ToolCallback delegate, ToolEventRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        ToolEventSink sink = resolveSink(toolContext);
        String callId = UUID.randomUUID().toString();
        String name = delegate.getToolDefinition().name();
        int inputLen = toolInput == null ? 0 : toolInput.length();
        String commandPreview = ChatAuditLog.isShellLikeTool(name)
                ? ChatAuditLog.shellCommandPreview(toolInput)
                : null;
        ChatAuditLog.toolStart(name, callId, inputLen, commandPreview);
        if (sink != null) {
            sink.toolCall(callId, name, toolInput);
        }
        long startNanos = System.nanoTime();
        try {
            String output = delegate.call(toolInput, toolContext);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int outputLen = output == null ? 0 : output.length();
            ChatAuditLog.toolEnd(name, callId, durationMs, outputLen, false);
            ChatAuditLog.toolVerbose(name, callId, toolInput, output, false);
            if (sink != null) {
                sink.toolResult(callId, output, false);
            }
            return output;
        } catch (RuntimeException e) {
            // Surface the failure to the UI (error card) AND hand it back to the model as the tool
            // output instead of rethrowing. Rethrowing aborts the whole turn ("Stream processing
            // failed") and the model never learns the call failed; returning the message lets it
            // recover (retry with a corrected payload) or honestly report the failure.
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            String message = "TOOL_ERROR: " + e.getMessage();
            ChatAuditLog.toolEnd(name, callId, durationMs, message.length(), true);
            ChatAuditLog.toolVerbose(name, callId, toolInput, message, true);
            if (sink != null) {
                sink.toolResult(callId, message, true);
            }
            return message;
        }
    }

    private ToolEventSink resolveSink(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object requestId = toolContext.getContext().get(ToolEventRegistry.REQUEST_ID);
        if (requestId == null) {
            return null;
        }
        return registry.get(requestId.toString()).orElse(null);
    }
}
