package com.ksp.agent.mcp.service;

import com.ksp.agent.mcp.dto.request.CreateMcpServerRequest;
import com.ksp.agent.mcp.dto.request.UpdateMcpServerRequest;
import com.ksp.agent.mcp.dto.response.McpServerDto;
import com.ksp.agent.mcp.dto.response.McpServerToolDto;

import java.util.List;

public interface McpServerService {

    List<McpServerDto> list(String assistantId);

    McpServerDto get(String id);

    McpServerDto create(String assistantId, CreateMcpServerRequest request);

    McpServerDto update(String id, UpdateMcpServerRequest request);

    void delete(String id);

    /** Connect to the server, list its tools, persist them, and update the connection status. */
    McpServerDto discover(String id);

    List<McpServerToolDto> listTools(String id);

    McpServerToolDto setToolEnabled(String toolId, boolean enabled);
}
