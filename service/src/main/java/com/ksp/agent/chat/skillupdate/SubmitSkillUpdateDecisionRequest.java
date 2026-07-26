package com.ksp.agent.chat.skillupdate;

public record SubmitSkillUpdateDecisionRequest(
        String requestId,
        boolean approved,
        String rejectionReason
) {
}
