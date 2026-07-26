package com.ksp.agent.chat.skillupdate;

import com.ksp.agent.skill.repo.AgentSkillRevisionRepository;
import com.ksp.agent.skill.service.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SkillUpdateBridgeTest {

    private SkillService skillService;
    private AgentSkillRevisionRepository revisionRepository;
    private SkillUpdateBridge bridge;

    @BeforeEach
    void setUp() {
        skillService = mock(SkillService.class);
        revisionRepository = mock(AgentSkillRevisionRepository.class);
        bridge = new SkillUpdateBridge(skillService, revisionRepository);
        ReflectionTestUtils.setField(bridge, "timeoutMinutes", 1);
    }

    @Test
    void submitDecisionApprovesAndUpdatesSkill() {
        AtomicReference<SkillUpdateProposalDto> emitted = new AtomicReference<>();
        CompletableFuture<SkillUpdateDecision> waiter = CompletableFuture.supplyAsync(() ->
                bridge.awaitDecision(
                        "req-1",
                        "session-1",
                        0,
                        "call-1",
                        "admin@example.com",
                        "asst-1",
                        new SkillUpdateProposalDto(
                                "req-1", "call-1", "skill-1", "Demo", "SKILL.md",
                                "summary", null, "old", "new"),
                        emitted::set));

        awaitUntil(() -> emitted.get() != null);

        assertThat(bridge.submitDecision("req-1", "admin@example.com",
                new SkillUpdateDecision(true, null))).isTrue();

        SkillUpdateDecision decision = waiter.join();
        assertThat(decision.approved()).isTrue();
        verify(skillService).updateFileContent("skill-1", "SKILL.md", "new");
        verify(revisionRepository).create(any(), any(Long.class));
    }

    @Test
    void submitDecisionRejectSkipsSkillUpdate() {
        AtomicReference<SkillUpdateProposalDto> emitted = new AtomicReference<>();
        CompletableFuture<SkillUpdateDecision> waiter = CompletableFuture.supplyAsync(() ->
                bridge.awaitDecision(
                        "req-2",
                        "session-2",
                        1,
                        "call-2",
                        "admin@example.com",
                        "asst-1",
                        new SkillUpdateProposalDto(
                                "req-2", "call-2", "skill-1", "Demo", "SKILL.md",
                                "summary", "fix colors", "old", "new"),
                        emitted::set));

        awaitUntil(() -> emitted.get() != null);

        assertThat(bridge.submitDecision("req-2", "admin@example.com",
                new SkillUpdateDecision(false, "Not now"))).isTrue();

        SkillUpdateDecision decision = waiter.join();
        assertThat(decision.approved()).isFalse();
        assertThat(decision.rejectionReason()).isEqualTo("Not now");
        verify(skillService, never()).updateFileContent(any(), any(), any());
        verify(revisionRepository).create(any(), any(Long.class));
    }

    @Test
    void callIdForReturnsRegisteredCallId() {
        bridge.registerToolCall("req-3", "session-3", 0, "tool-call-id");
        assertThat(bridge.callIdFor("req-3")).isEqualTo("tool-call-id");
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 2000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
    }
}
