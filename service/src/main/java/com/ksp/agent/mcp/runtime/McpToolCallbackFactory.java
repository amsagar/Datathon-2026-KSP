package com.ksp.agent.mcp.runtime;

import com.ksp.agent.mcp.entity.McpServer;
import com.ksp.agent.mcp.entity.McpServerTool;
import com.ksp.agent.mcp.repo.McpServerRepository;
import com.ksp.agent.mcp.repo.McpServerToolRepository;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds runtime {@link ToolCallback}s for the enabled MCP tools of an assistant's enabled servers.
 * Mirrors {@code HttpToolCallbackFactory}: it returns callbacks of the exact type the chat pipeline
 * consumes, so MCP tools participate in event instrumentation and search-mode gating unchanged.
 *
 * Connection failures are logged and skipped (fail-open) so a broken MCP server never breaks a chat.
 * The returned {@link AssistantMcpTools} owns the live clients; the caller must close it.
 */
@Component
@Slf4j
public class McpToolCallbackFactory {

    private final McpServerRepository serverRepository;
    private final McpServerToolRepository serverToolRepository;
    private final McpClientFactory clientFactory;

    public McpToolCallbackFactory(McpServerRepository serverRepository,
                                  McpServerToolRepository serverToolRepository,
                                  McpClientFactory clientFactory) {
        this.serverRepository = serverRepository;
        this.serverToolRepository = serverToolRepository;
        this.clientFactory = clientFactory;
    }

    public AssistantMcpTools callbacksForAssistant(String assistantId) {
        if (assistantId == null || assistantId.isBlank()) {
            return AssistantMcpTools.empty();
        }

        List<McpServer> servers = serverRepository.findEnabledByAssistant(assistantId);
        List<McpSyncClient> clients = new ArrayList<>();
        Set<String> enabledToolNames = new HashSet<>();

        for (McpServer server : servers) {
            List<McpServerTool> enabledTools = serverToolRepository.findEnabledByServer(server.getId());
            if (enabledTools.isEmpty()) {
                // No tools discovered/enabled yet — nothing to expose, skip connecting.
                continue;
            }
            try {
                McpSyncClient client = clientFactory.buildClient(server);
                clients.add(client);
                enabledTools.forEach(t -> enabledToolNames.add(t.getName()));
            } catch (Exception e) {
                log.warn("Skipping MCP server {} ({}): {}", server.getName(), server.getId(), e.getMessage());
                safeMarkError(server, e.getMessage());
            }
        }

        if (clients.isEmpty()) {
            return AssistantMcpTools.empty();
        }

        // Only expose tools the assistant has enabled (matched on the raw MCP tool name).
        McpToolFilter filter = (connectionInfo, tool) -> enabledToolNames.contains(tool.name());
        try {
            ToolCallback[] callbacks = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(clients)
                    .toolFilter(filter)
                    .build()
                    .getToolCallbacks();
            return new AssistantMcpTools(new ArrayList<>(List.of(callbacks)), clients);
        } catch (Exception e) {
            log.warn("Failed to build MCP tool callbacks for assistant {}: {}", assistantId, e.getMessage());
            clients.forEach(McpClientFactory::closeQuietly);
            return AssistantMcpTools.empty();
        }
    }

    private void safeMarkError(McpServer server, String detail) {
        try {
            serverRepository.updateStatus(server.getId(), "error", detail, Instant.now().getEpochSecond());
        } catch (RuntimeException ignored) {
            // best-effort status update
        }
    }
}
