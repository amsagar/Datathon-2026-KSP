package com.ksp.agent.chat.skillupdate;

import com.ksp.agent.chat.repo.ChatToolEventRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists {@code propose_skill_update} turns in {@code chat_tool_event} so proposals survive reload.
 */
@Component
public class SkillUpdatePersistence {

    private final ChatToolEventRepository toolEventRepository;
    private final ObjectMapper objectMapper;

    public SkillUpdatePersistence(ChatToolEventRepository toolEventRepository,
                                  ObjectMapper objectMapper) {
        this.toolEventRepository = toolEventRepository;
        this.objectMapper = objectMapper;
    }

    public void saveToolCall(String sessionId, int turnIndex, int seq, String callId,
                             String toolInput, long now) {
        toolEventRepository.save(sessionId, turnIndex, seq, callId,
                com.ksp.agent.chat.tooling.SkillUpdateToolFactory.TOOL_NAME,
                toolInput, "", false, now);
    }

    public void saveProposalPayload(String sessionId, String callId, SkillUpdateProposalDto proposal) {
        if (sessionId == null || callId == null || proposal == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("skillId", proposal.skillId());
            payload.put("skillName", proposal.skillName());
            payload.put("filePath", proposal.filePath());
            payload.put("summary", proposal.summary());
            payload.put("feedbackQuote", proposal.feedbackQuote());
            payload.put("currentContent", proposal.currentContent());
            payload.put("proposedContent", proposal.proposedContent());
            String json = objectMapper.writeValueAsString(payload);
            toolEventRepository.updateInput(sessionId, callId, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist skill update proposal", e);
        }
    }

    public void saveDecision(String sessionId, String callId, SkillUpdateDecision decision) {
        if (sessionId == null || callId == null || decision == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("approved", decision.approved());
            if (decision.rejectionReason() != null && !decision.rejectionReason().isBlank()) {
                payload.put("rejectionReason", decision.rejectionReason());
            }
            String json = objectMapper.writeValueAsString(payload);
            toolEventRepository.updateOutput(sessionId, callId, json, !decision.approved());
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist skill update decision", e);
        }
    }
}
