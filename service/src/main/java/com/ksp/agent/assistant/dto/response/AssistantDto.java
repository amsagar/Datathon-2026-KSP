package com.ksp.agent.assistant.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssistantDto {
    private String id;
    private String name;
    private String systemPrompt;
    private List<String> builtinTools;
    /** Resolved ACTIVE platform skill ids (default semantics already applied), never null. */
    private List<String> platformSkills;
    private Long createdAt;
    private Long updatedAt;
}
