package com.ksp.agent.mcp.runtime;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * The MCP tool callbacks resolved for one assistant turn, together with the live clients backing
 * them. The clients stay open for the duration of the turn and must be {@link #close() closed} when
 * the turn ends (the chat controller does this in its doFinally).
 */
public record AssistantMcpTools(List<ToolCallback> callbacks, List<McpSyncClient> clients) {

    public static AssistantMcpTools empty() {
        return new AssistantMcpTools(List.of(), List.of());
    }

    public void close() {
        for (McpSyncClient client : clients) {
            McpClientFactory.closeQuietly(client);
        }
    }
}
