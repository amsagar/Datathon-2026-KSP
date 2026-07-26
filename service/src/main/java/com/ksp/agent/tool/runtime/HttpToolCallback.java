package com.ksp.agent.tool.runtime;

import com.ksp.agent.tool.entity.AgentTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Runtime {@link ToolCallback} backed by an {@link AgentTool}. Exposes the tool's
 * request schema to the model and performs the outbound HTTP call via
 * {@link HttpToolExecutor} when invoked.
 */
public class HttpToolCallback implements ToolCallback {

    private static final String EMPTY_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private final AgentTool tool;
    private final HttpToolExecutor executor;

    public HttpToolCallback(AgentTool tool, HttpToolExecutor executor) {
        this.tool = tool;
        this.executor = executor;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DefaultToolDefinition.builder()
                .name(ToolNames.sanitize(tool.getName()))
                .description(tool.getDescription() == null ? "" : tool.getDescription())
                .inputSchema(resolveSchema())
                .build();
    }

    @Override
    public String call(String toolInput) {
        return executor.execute(tool, toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return executor.execute(tool, toolInput);
    }

    private String resolveSchema() {
        String raw = tool.getRequestSchema();
        return raw == null || raw.isBlank() ? EMPTY_SCHEMA : raw;
    }
}
