package com.ksp.agent.tool.service;

import com.ksp.agent.tool.dto.request.CreateToolRequest;
import com.ksp.agent.tool.dto.request.TestToolRequest;
import com.ksp.agent.tool.dto.request.UpdateToolRequest;
import com.ksp.agent.tool.dto.response.AgentToolDto;
import com.ksp.agent.tool.dto.response.TestToolResult;
import com.ksp.agent.tool.entity.AgentTool;

import java.util.List;

public interface AgentToolService {

    List<AgentToolDto> list(String assistantId);

    AgentToolDto get(String id);

    AgentToolDto create(String assistantId, CreateToolRequest request);

    AgentToolDto update(String id, UpdateToolRequest request);

    void delete(String id);

    TestToolResult test(String id, TestToolRequest request);

    AgentTool requireEntity(String id);

    AgentToolDto persistImported(String assistantId, AgentTool tool);
}
