package com.ksp.agent.skill.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentSkillRevision {
    private String id;
    private String skillId;
    private String assistantId;
    private String filePath;
    private String summary;
    private String feedbackQuote;
    private boolean approved;
    private String decidedBy;
    private String sessionId;
    private String requestId;
    private Long createdAt;
}
