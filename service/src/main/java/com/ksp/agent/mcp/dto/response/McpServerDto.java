package com.ksp.agent.mcp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Client-facing view of an MCP server. Never exposes secrets — {@code hasSecret} only signals
 * whether one is stored. {@code tools} is populated on get/discover, null on the list view.
 */
@Data
@Builder
public class McpServerDto {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private String transport;
    private String url;
    private String sseEndpoint;
    private String authType;
    private String authConfig;
    private boolean hasSecret;
    private boolean enabled;
    private String status;
    private String statusDetail;
    private Long createdAt;
    private Long updatedAt;
    private List<McpServerToolDto> tools;
}
