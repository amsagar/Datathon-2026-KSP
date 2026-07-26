package com.ksp.agent.chat.controller;

import com.ksp.agent.chat.tooling.SkillUpdateToolFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatControllerSkillUpdateScopeTest {

    @Test
    void isSkillUpdateRequestDetectsSkillCsvUpdate() {
        assertThat(ChatController.isSkillUpdateRequest(
                "Update the skill add this row after Hawaii: Test Journey,NEW -> MOV -> FPU"))
                .isTrue();
    }

    @Test
    void isSkillUpdateRequestDetectsLegSequencesPath() {
        assertThat(ChatController.isSkillUpdateRequest(
                "Edit references/leg-sequences.csv and add a row"))
                .isTrue();
    }

    @Test
    void isSkillUpdateRequestDetectsProposeToolName() {
        assertThat(ChatController.isSkillUpdateRequest(
                "Use propose_skill_update for leg-sequences.csv"))
                .isTrue();
    }

    @Test
    void isSkillUpdateRequestDetectsApprovalPhrasing() {
        assertThat(ChatController.isSkillUpdateRequest(
                "Propose the update and wait for my approval"))
                .isTrue();
    }

    @Test
    void isSkillUpdateRequestRejectsOrderValidation() {
        assertThat(ChatController.isSkillUpdateRequest("Validate order 5038081"))
                .isFalse();
    }

    @Test
    void isSkillUpdateRequestRejectsBlank() {
        assertThat(ChatController.isSkillUpdateRequest("")).isFalse();
        assertThat(ChatController.isSkillUpdateRequest(null)).isFalse();
    }

    @Test
    void scopeGuardToolSummaryIncludesToolName() {
        assertThat(SkillUpdateToolFactory.scopeGuardToolSummary())
                .contains(SkillUpdateToolFactory.TOOL_NAME)
                .contains("uploaded skill");
    }
}
