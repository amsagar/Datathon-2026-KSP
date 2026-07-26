package com.ksp.agent.style.service;

import com.ksp.agent.style.dto.request.CreateStyleRequest;
import com.ksp.agent.style.dto.request.UpdateStyleRequest;
import com.ksp.agent.style.dto.response.ResponseStyleDto;
import com.ksp.agent.style.entity.ResponseStyle;

import java.util.List;

public interface ResponseStyleService {

    List<ResponseStyleDto> list(String assistantId);

    ResponseStyleDto get(String id);

    ResponseStyleDto create(String assistantId, CreateStyleRequest request);

    ResponseStyleDto update(String id, UpdateStyleRequest request);

    void delete(String id);

    ResponseStyleDto setDefault(String id);

    ResponseStyle requireEntity(String id);

    /** Resolve a style's instructions by id, or {@code null} if the id is blank or unknown. */
    String instructionsFor(String styleId);

    /** Default style for an assistant, if one is starred in settings. */
    String defaultStyleIdForAssistant(String assistantId);
}
