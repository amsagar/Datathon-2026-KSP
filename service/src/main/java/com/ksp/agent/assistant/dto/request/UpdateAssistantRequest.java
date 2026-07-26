package com.ksp.agent.assistant.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateAssistantRequest {
    private String name;
    private String systemPrompt;
    private List<String> builtinTools;
    /** null = leave unchanged; non-null = explicit active id list ([] disables all). */
    private List<String> platformSkills;
}
