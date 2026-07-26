package com.ksp.agent.assistant.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Assistant {
    private String id;
    private String name;
    private String systemPrompt;
    private String builtinTools;
    // NULL = platform-skill defaults apply; non-null = explicit comma-separated id list.
    private String platformSkills;
    private Long createdAt;
    private Long updatedAt;
}
