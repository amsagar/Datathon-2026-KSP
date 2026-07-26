package com.ksp.agent.chat.skillupdate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillUpdateProposalValidatorTest {

    private static final String REFERENCE_CSV = "references/crime-code-taxonomy.csv";

    private static final String CURRENT = """
            code,category,description
            IPC-379,theft,Theft of movable property
            """;

    @Test
    void acceptsMinimalRowAddition() {
        String proposed = CURRENT + "IPC-392,robbery,Robbery\n";
        assertThat(SkillUpdateProposalValidator.validate(REFERENCE_CSV, CURRENT, proposed).valid())
                .isTrue();
    }

    @Test
    void rejectsHeaderChange() {
        String proposed = """
                Code,Section,Notes
                IPC-379,theft,
                """;
        assertThat(SkillUpdateProposalValidator.validate(REFERENCE_CSV, CURRENT, proposed).valid())
                .isFalse();
    }

    @Test
    void rejectsExistingRowRemovedOrAltered() {
        String proposed = """
                code,category,description
                IPC-379,theft,Different wording for the same offence
                IPC-392,robbery,Robbery
                """;
        assertThat(SkillUpdateProposalValidator.validate(REFERENCE_CSV, CURRENT, proposed).valid())
                .isFalse();
    }

    @Test
    void skipsValidationForNonCsv() {
        assertThat(SkillUpdateProposalValidator.validate("SKILL.md", "# Skill", "# Changed").valid())
                .isTrue();
    }
}
