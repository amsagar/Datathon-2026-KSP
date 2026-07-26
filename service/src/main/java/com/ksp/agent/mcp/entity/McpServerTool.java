package com.ksp.agent.mcp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A tool discovered from an MCP server. Persisted per server with a per-tool {@code enabled} flag so
 * the owning assistant can expose a subset of the server's tools to the model.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class McpServerTool {
    private String id;
    private String serverId;
    private String name;
    private String description;
    private String inputSchema; // JSON schema as discovered
    private boolean enabled;
    private Long createdAt;
    private Long updatedAt;
}
