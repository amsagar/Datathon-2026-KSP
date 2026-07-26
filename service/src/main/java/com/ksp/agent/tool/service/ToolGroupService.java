package com.ksp.agent.tool.service;

import com.ksp.agent.tool.dto.request.CreateToolGroupRequest;
import com.ksp.agent.tool.dto.request.UpdateToolGroupRequest;
import com.ksp.agent.tool.dto.response.ToolGroupDto;
import com.ksp.agent.tool.entity.AgentToolGroup;

import java.util.List;

public interface ToolGroupService {

    List<ToolGroupDto> list(String assistantId);

    ToolGroupDto get(String id);

    ToolGroupDto create(String assistantId, CreateToolGroupRequest request);

    ToolGroupDto createImported(String assistantId, String name, String description, String sourceType);

    ToolGroupDto update(String id, UpdateToolGroupRequest request);

    void delete(String id);

    /** Groups legacy openapi/postman imports that have no group_id yet. */
    int backfillUngroupedImports(String assistantId);

    AgentToolGroup requireEntity(String id);
}
