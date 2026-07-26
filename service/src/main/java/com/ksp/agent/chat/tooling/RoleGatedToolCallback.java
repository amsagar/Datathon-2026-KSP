package com.ksp.agent.chat.tooling;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Denies a tool call outright for callers without an investigative role, returning a JSON error
 * instead of delegating. {@code allowed} is decided once, eagerly, on the request thread (see
 * {@code ChatController.buildTurnFlux}) and closed over here — {@code SecurityContextHolder} is
 * empty on the Spring AI tool-execution thread this callback's {@code call(...)} runs on, so the
 * role check cannot be done lazily inside the delegate tool itself.
 */
public class RoleGatedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final boolean allowed;

    public RoleGatedToolCallback(ToolCallback delegate, boolean allowed) {
        this.delegate = delegate;
        this.allowed = allowed;
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
        return allowed ? delegate.call(toolInput) : denied();
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return allowed ? delegate.call(toolInput, toolContext) : denied();
    }

    private String denied() {
        return "{\"error\":\"" + delegate.getToolDefinition().name()
                + " requires an investigative role (ADMIN, SUPERVISOR or INVESTIGATOR).\"}";
    }
}
