package com.ksp.agent.chat.tooling;

import com.ksp.agent.chat.audit.ChatAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Meta-tool that executes a tool previously discovered via {@link SearchToolsCallback}. The model
 * passes the exact tool name plus an arguments object; this resolves the real (already instrumented)
 * callback from {@link DynamicToolRegistry} and delegates to it, so the underlying tool still emits
 * tool-call/result events (UI cards + persistence) under its own name.
 */
@Component
public class InvokeToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(InvokeToolCallback.class);

    public static final String NAME = "invoke_tool";

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "Exact name of the tool to invoke, as returned by search_tools."
                },
                "input": {
                  "type": "object",
                  "description": "Arguments object matching the chosen tool's input schema."
                }
              },
              "required": ["name"]
            }
            """;

    private final DynamicToolRegistry registry;
    private final ObjectMapper objectMapper;

    public InvokeToolCallback(DynamicToolRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Invoke a tool that was returned by search_tools. Provide the tool's exact "
                        + "name and an 'input' object whose fields match that tool's input schema.")
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String requestId = resolveRequestId(toolContext);
        DynamicToolRegistry.Entry entry = registry.get(requestId);
        Map<String, ToolCallback> catalog = entry == null ? Map.of() : entry.tools();

        String name;
        String args;
        try {
            Map<?, ?> in = objectMapper.readValue(toolInput, Map.class);
            Object nameObj = in.get("name");
            name = nameObj == null ? null : String.valueOf(nameObj);
            Object input = in.get("input");
            if (input == null) {
                args = "{}";
            } else if (input instanceof String s) {
                args = s;
            } else {
                args = objectMapper.writeValueAsString(input);
            }
        } catch (Exception e) {
            return "{\"error\":\"invoke_tool: could not parse input JSON\"}";
        }

        if (name == null || name.isBlank()) {
            return "{\"error\":\"invoke_tool: 'name' is required\"}";
        }
        ToolCallback target = catalog.get(name);
        if (target == null) {
            log.warn("invoke_tool: unknown tool '{}'", name);
            return "{\"error\":\"Unknown tool: " + name + ". Use search_tools to find valid tool names.\"}";
        }
        ChatAuditLog.invokeToolDelegate(name);
        return target.call(args, toolContext);
    }

    private String resolveRequestId(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object requestId = toolContext.getContext().get(ToolEventRegistry.REQUEST_ID);
        return requestId == null ? null : requestId.toString();
    }
}
