package com.ksp.agent.assistant.service;

import com.ksp.agent.assistant.dto.request.CreateAssistantRequest;
import com.ksp.agent.assistant.dto.request.UpdateAssistantRequest;
import com.ksp.agent.assistant.dto.response.AssistantDto;
import com.ksp.agent.assistant.dto.response.BuiltinToolDto;
import com.ksp.agent.assistant.entity.Assistant;

import java.util.List;

public interface AssistantService {

    List<AssistantDto> list();

    AssistantDto get(String id);

    AssistantDto create(CreateAssistantRequest request);

    AssistantDto update(String id, UpdateAssistantRequest request);

    void delete(String id);

    List<BuiltinToolDto> builtinCatalog();

    Assistant requireEntity(String id);

    List<String> builtinToolKeys(Assistant assistant);

    /** Active platform skill ids for the assistant (default semantics already applied). */
    List<String> activePlatformSkillIds(Assistant assistant);

    String defaultAssistantId();
}
