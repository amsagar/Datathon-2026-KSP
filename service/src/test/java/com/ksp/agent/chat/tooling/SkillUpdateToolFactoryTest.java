package com.ksp.agent.chat.tooling;

import com.ksp.agent.chat.skillupdate.SkillUpdateBridge;
import com.ksp.agent.chat.skillupdate.SkillUpdateDecision;
import com.ksp.agent.chat.skillupdate.SkillUpdatePersistence;
import com.ksp.agent.skill.dto.response.SkillDto;
import com.ksp.agent.skill.dto.response.SkillFileContentDto;
import com.ksp.agent.skill.service.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillUpdateToolFactoryTest {

    private SkillService skillService;
    private SkillUpdateBridge skillUpdateBridge;
    private SkillUpdatePersistence skillUpdatePersistence;
    private SkillUpdateToolFactory factory;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        skillService = mock(SkillService.class);
        skillUpdateBridge = mock(SkillUpdateBridge.class);
        skillUpdatePersistence = mock(SkillUpdatePersistence.class);
        objectMapper = new ObjectMapper();
        factory = new SkillUpdateToolFactory(
                skillService, skillUpdateBridge, skillUpdatePersistence, objectMapper);
    }

    @Test
    void callbacksEmptyWhenNotAdmin() {
        Sinks.Many<org.springframework.http.codec.ServerSentEvent<Object>> sink =
                Sinks.many().multicast().onBackpressureBuffer();
        List<ToolCallback> tools = factory.callbacks(
                "req", "user", "asst", "session", 0, "req", false, sink,
                Sinks.EmitFailureHandler.FAIL_FAST, "en");
        assertThat(tools).isEmpty();
    }

    @Test
    void proposeSkillUpdateReturnsNoOpWhenContentUnchanged() {
        when(skillService.get("skill-1")).thenReturn(SkillDto.builder()
                .id("skill-1")
                .assistantId("asst-1")
                .name("Demo")
                .enabled(true)
                .build());
        when(skillService.getFileContent("skill-1", "SKILL.md"))
                .thenReturn(SkillFileContentDto.builder().path("SKILL.md").content("same").build());

        ToolCallback tool = factory.callbacks(
                "req", "admin", "asst-1", "session", 0, "req", true,
                Sinks.many().multicast().onBackpressureBuffer(),
                Sinks.EmitFailureHandler.FAIL_FAST, "en").get(0);

        String result = tool.call("""
                {"skillId":"skill-1","filePath":"SKILL.md","proposedContent":"same","summary":"noop"}
                """);

        assertThat(result).contains("No change needed");
        verify(skillUpdateBridge, never()).awaitDecision(
                any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void proposeSkillUpdateRejectsWrongAssistant() {
        when(skillService.get("skill-1")).thenReturn(SkillDto.builder()
                .id("skill-1")
                .assistantId("other-asst")
                .name("Demo")
                .enabled(true)
                .build());

        ToolCallback tool = factory.callbacks(
                "req", "admin", "asst-1", "session", 0, "req", true,
                Sinks.many().multicast().onBackpressureBuffer(),
                Sinks.EmitFailureHandler.FAIL_FAST, "en").get(0);

        String result = tool.call("""
                {"skillId":"skill-1","filePath":"SKILL.md","proposedContent":"new","summary":"change"}
                """);

        assertThat(result).contains("does not belong");
        verify(skillUpdateBridge, never()).awaitDecision(
                any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void proposeSkillUpdateWaitsForApproval() {
        when(skillService.get("skill-1")).thenReturn(SkillDto.builder()
                .id("skill-1")
                .assistantId("asst-1")
                .name("Demo")
                .enabled(true)
                .build());
        when(skillService.getFileContent("skill-1", "SKILL.md"))
                .thenReturn(SkillFileContentDto.builder().path("SKILL.md").content("old").build());
        when(skillUpdateBridge.callIdFor("req")).thenReturn("call-1");
        when(skillUpdateBridge.awaitDecision(
                any(), any(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new SkillUpdateDecision(true, null));

        ToolCallback tool = factory.callbacks(
                "req", "admin", "asst-1", "session", 0, "req", true,
                Sinks.many().multicast().onBackpressureBuffer(),
                Sinks.EmitFailureHandler.FAIL_FAST, "en").get(0);

        String result = tool.call("""
                {"skillId":"skill-1","filePath":"SKILL.md","proposedContent":"new","summary":"change"}
                """);

        assertThat(result).contains("approved and saved");
    }
}
