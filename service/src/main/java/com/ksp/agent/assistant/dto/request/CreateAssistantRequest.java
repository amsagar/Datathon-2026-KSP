package com.ksp.agent.assistant.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class CreateAssistantRequest {
    private String name;
    private String systemPrompt;
    private List<String> builtinTools;
    /** null = platform-skill defaults; non-null = explicit active id list. */
    private List<String> platformSkills;
}
