package com.ksp.agent.chat.tooling;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds, per in-flight request, the full catalog of (already instrumented) tool callbacks an
 * assistant has access to, keyed by tool name, along with the owning assistant id. When an
 * assistant has many tools we do NOT send them all to the model; instead we expose
 * {@code search_tools} + {@code invoke_tool}, and those resolve the real callbacks from here (by
 * the {@code requestId} carried in the ToolContext). The assistant id lets {@code search_tools}
 * scope its DB-side pgvector similarity query to just this assistant's tools.
 */
@Component
public class DynamicToolRegistry {

    /** A request's tool catalog plus the assistant that owns it. */
    public record Entry(String assistantId, Map<String, ToolCallback> tools) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public void register(String requestId, String assistantId, Map<String, ToolCallback> tools) {
        entries.put(requestId, new Entry(assistantId, tools));
    }

    public void unregister(String requestId) {
        entries.remove(requestId);
    }

    public Entry get(String requestId) {
        if (requestId == null) {
            return null;
        }
        return entries.get(requestId);
    }
}
