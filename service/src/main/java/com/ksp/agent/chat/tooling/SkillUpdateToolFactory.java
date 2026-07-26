package com.ksp.agent.chat.tooling;

import com.ksp.agent.chat.skillupdate.SkillUpdateBridge;
import com.ksp.agent.chat.skillupdate.SkillUpdateDecision;
import com.ksp.agent.chat.skillupdate.SkillUpdatePersistence;
import com.ksp.agent.chat.skillupdate.SkillUpdateProposalDto;
import com.ksp.agent.chat.skillupdate.SkillUpdateProposalValidator;
import com.ksp.agent.chat.sse.ChatSseEmitter;
import com.ksp.agent.chat.sse.ToolProgressCopy;
import com.ksp.agent.skill.dto.response.SkillDto;
import com.ksp.agent.skill.dto.response.SkillFileContentDto;
import com.ksp.agent.skill.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Builds the admin-only {@code propose_skill_update} tool for one chat turn. Identity and admin
 * status are captured on the request thread (where SecurityContext is valid).
 */
@Component
public class SkillUpdateToolFactory {

    private static final Logger log = LoggerFactory.getLogger(SkillUpdateToolFactory.class);

    public static final String TOOL_NAME = "propose_skill_update";

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "skillId": { "type": "string", "description": "UUID of the uploaded skill to update." },
                "filePath": { "type": "string", "description": "Relative path within the skill bundle, e.g. SKILL.md." },
                "proposedContent": { "type": "string", "description": "The complete new file content." },
                "summary": { "type": "string", "description": "Short rationale shown to the admin in the approval card." },
                "feedbackQuote": { "type": "string", "description": "Optional quote from the admin message that triggered this change." }
              },
              "required": ["skillId", "filePath", "proposedContent", "summary"]
            }
            """;

    private final SkillService skillService;
    private final SkillUpdateBridge skillUpdateBridge;
    private final SkillUpdatePersistence skillUpdatePersistence;
    private final ObjectMapper objectMapper;

    public SkillUpdateToolFactory(SkillService skillService,
                                  SkillUpdateBridge skillUpdateBridge,
                                  SkillUpdatePersistence skillUpdatePersistence,
                                  ObjectMapper objectMapper) {
        this.skillService = skillService;
        this.skillUpdateBridge = skillUpdateBridge;
        this.skillUpdatePersistence = skillUpdatePersistence;
        this.objectMapper = objectMapper;
    }

    public static boolean isSkillUpdateTool(String name) {
        return TOOL_NAME.equals(name);
    }

    /** Static summary for the scope-guard classifier before full tool callbacks are registered. */
    public static String scopeGuardToolSummary() {
        return TOOL_NAME + " — Propose an update to an uploaded skill file; admin must approve before saving.";
    }

    public List<ToolCallback> callbacks(String requestId,
                                        String userId,
                                        String assistantId,
                                        String sessionId,
                                        int turnIndex,
                                        String callIdPlaceholder,
                                        boolean admin,
                                        Sinks.Many<ServerSentEvent<Object>> toolSink,
                                        Sinks.EmitFailureHandler emitHandler,
                                        String lang) {
        if (!admin) {
            return List.of();
        }
        return List.of(new ProposeSkillUpdateCallback(
                requestId, userId, assistantId, sessionId, turnIndex, toolSink, emitHandler, lang));
    }

    private final class ProposeSkillUpdateCallback implements ToolCallback {
        private final String requestId;
        private final String userId;
        private final String assistantId;
        private final String sessionId;
        private final int turnIndex;
        private final Sinks.Many<ServerSentEvent<Object>> toolSink;
        private final Sinks.EmitFailureHandler emitHandler;
        private final String lang;

        private ProposeSkillUpdateCallback(String requestId,
                                           String userId,
                                           String assistantId,
                                           String sessionId,
                                           int turnIndex,
                                           Sinks.Many<ServerSentEvent<Object>> toolSink,
                                           Sinks.EmitFailureHandler emitHandler,
                                           String lang) {
            this.requestId = requestId;
            this.userId = userId;
            this.assistantId = assistantId;
            this.sessionId = sessionId;
            this.turnIndex = turnIndex;
            this.toolSink = toolSink;
            this.emitHandler = emitHandler;
            this.lang = lang;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(TOOL_NAME)
                    .description("Propose an update to an uploaded skill file for this assistant. "
                            + "The admin must approve before changes are saved. Use when the admin "
                            + "gives feedback about skill behavior or explicitly asks to change a skill file. "
                            + "You MUST read the current file content first (getFileContent API or skill "
                            + "file tools), preserve its format and headers, and apply only the requested "
                            + "change — never replace a CSV with a different schema or invent Step-column "
                            + "layouts. Provide the full revised file content — partial patches are not supported.")
                    .inputSchema(INPUT_SCHEMA)
                    .build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String skillId;
            String filePath;
            String proposedContent;
            String summary;
            String feedbackQuote = null;
            try {
                Map<?, ?> in = objectMapper.readValue(toolInput, Map.class);
                skillId = str(in.get("skillId"));
                filePath = str(in.get("filePath"));
                proposedContent = str(in.get("proposedContent"));
                summary = str(in.get("summary"));
                feedbackQuote = str(in.get("feedbackQuote"));
            } catch (Exception e) {
                return "Could not propose skill update: input must be JSON with skillId, filePath, proposedContent, and summary.";
            }
            if (skillId == null || filePath == null || proposedContent == null || summary == null) {
                return "Could not propose skill update: skillId, filePath, proposedContent, and summary are all required.";
            }
            if (filePath.contains("..") || filePath.startsWith("/")) {
                return "Could not propose skill update: invalid file path.";
            }

            SkillDto skill;
            try {
                skill = skillService.get(skillId);
            } catch (RuntimeException e) {
                return "Could not propose skill update: skill not found.";
            }
            if (!assistantId.equals(skill.getAssistantId())) {
                return "Could not propose skill update: that skill does not belong to this assistant.";
            }

            SkillFileContentDto current;
            try {
                current = skillService.getFileContent(skillId, filePath);
            } catch (RuntimeException e) {
                return "Could not propose skill update: " + e.getMessage();
            }
            if (proposedContent.equals(current.getContent())) {
                return "No change needed — proposed content is identical to the current file.";
            }

            SkillUpdateProposalValidator.ValidationResult validation =
                    SkillUpdateProposalValidator.validate(filePath, current.getContent(), proposedContent);
            if (!validation.valid()) {
                return validation.message();
            }

            String callId = skillUpdateBridge.callIdFor(requestId);
            if (callId == null) {
                callId = requestId;
            }
            SkillUpdateProposalDto proposal = new SkillUpdateProposalDto(
                    requestId,
                    callId,
                    skillId,
                    skill.getName(),
                    filePath,
                    summary,
                    feedbackQuote,
                    current.getContent(),
                    proposedContent);

            try {
                skillUpdatePersistence.saveProposalPayload(sessionId, callId, proposal);
            } catch (RuntimeException e) {
                log.warn("Failed to persist skill update proposal for session {}: {}", sessionId, e.getMessage());
            }

            try {
                SkillUpdateDecision decision = skillUpdateBridge.awaitDecision(
                        requestId,
                        sessionId,
                        turnIndex,
                        callId,
                        userId,
                        assistantId,
                        proposal,
                        event -> emitProposal(event));
                try {
                    skillUpdatePersistence.saveDecision(sessionId, callId, decision);
                } catch (RuntimeException e) {
                    log.warn("Failed to persist skill update decision for session {}: {}", sessionId, e.getMessage());
                }
                if (decision.approved()) {
                    return "Skill update approved and saved: " + skill.getName() + " / " + filePath;
                }
                String reason = decision.rejectionReason();
                if (reason != null && !reason.isBlank()) {
                    return "Skill update rejected by admin: " + reason;
                }
                return "Skill update rejected by admin.";
            } catch (RuntimeException e) {
                log.warn("propose_skill_update failed for session {}: {}", sessionId, e.getMessage());
                return "Could not complete skill update proposal: " + e.getMessage();
            }
        }

        private void emitProposal(SkillUpdateProposalDto event) {
            toolSink.emitNext(
                    ChatSseEmitter.status(ToolProgressCopy.reviewSkillUpdate(lang)),
                    emitHandler);
            toolSink.emitNext(
                    ServerSentEvent.builder((Object) event).event("skill_update_proposal").build(),
                    emitHandler);
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).strip();
        return s.isBlank() ? null : s;
    }
}
