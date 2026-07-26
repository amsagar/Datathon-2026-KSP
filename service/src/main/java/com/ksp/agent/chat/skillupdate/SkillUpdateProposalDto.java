package com.ksp.agent.chat.skillupdate;

public record SkillUpdateProposalDto(
        String requestId,
        String callId,
        String skillId,
        String skillName,
        String filePath,
        String summary,
        String feedbackQuote,
        String currentContent,
        String proposedContent
) {
}
