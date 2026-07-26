package com.ksp.agent.mcp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpServerToolDto {
    private String id;
    private String serverId;
    private String name;
    private String description;
    private String inputSchema;
    private boolean enabled;
    private Long createdAt;
    private Long updatedAt;
}
