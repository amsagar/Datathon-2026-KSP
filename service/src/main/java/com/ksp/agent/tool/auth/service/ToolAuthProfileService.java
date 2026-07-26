package com.ksp.agent.tool.auth.service;

import com.ksp.agent.tool.auth.dto.request.CreateAuthProfileRequest;
import com.ksp.agent.tool.auth.dto.request.UpdateAuthProfileRequest;
import com.ksp.agent.tool.auth.dto.response.ToolAuthProfileDto;
import com.ksp.agent.tool.auth.entity.ToolAuthProfile;

import java.util.List;

public interface ToolAuthProfileService {

    List<ToolAuthProfileDto> list(String assistantId);

    ToolAuthProfileDto get(String id);

    ToolAuthProfileDto create(String assistantId, CreateAuthProfileRequest request);

    ToolAuthProfileDto update(String id, UpdateAuthProfileRequest request);

    void delete(String id);

    ToolAuthProfile requireEntity(String id);
}
