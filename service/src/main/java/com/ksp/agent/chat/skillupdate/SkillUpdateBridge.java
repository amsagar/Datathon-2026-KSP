package com.ksp.agent.chat.skillupdate;

import com.ksp.agent.skill.entity.AgentSkillRevision;
import com.ksp.agent.skill.repo.AgentSkillRevisionRepository;
import com.ksp.agent.skill.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Bridges the synchronous {@code propose_skill_update} tool to async web UI: emits a structured
 * proposal, blocks on a {@link CompletableFuture}, and resumes when the admin POSTs a decision.
 */
@Component
public class SkillUpdateBridge {

    private static final Logger log = LoggerFactory.getLogger(SkillUpdateBridge.class);

    private record PendingMeta(
            String requestId,
            String sessionId,
            int turnIndex,
            String callId,
            String userId,
            String assistantId,
            String skillId,
            String skillName,
            String filePath,
            String summary,
            String feedbackQuote,
            String currentContent,
            String proposedContent
    ) {}

    private final SkillService skillService;
    private final AgentSkillRevisionRepository revisionRepository;
    private final ConcurrentHashMap<String, CompletableFuture<SkillUpdateDecision>> pending =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingMeta> pendingMeta = new ConcurrentHashMap<>();

    @Value("${agent.skill-update.timeout-minutes:10}")
    private int timeoutMinutes;

    public SkillUpdateBridge(SkillService skillService,
                             AgentSkillRevisionRepository revisionRepository) {
        this.skillService = skillService;
        this.revisionRepository = revisionRepository;
    }

    public void registerToolCall(String requestId, String sessionId, int turnIndex, String callId) {
        if (requestId != null && sessionId != null && callId != null) {
            pendingMeta.put(requestId, new PendingMeta(
                    requestId, sessionId, turnIndex, callId,
                    null, null, null, null, null, null, null, null, null));
        }
    }

    public String callIdFor(String requestId) {
        PendingMeta meta = pendingMeta.get(requestId);
        return meta != null ? meta.callId() : null;
    }

    public SkillUpdateDecision awaitDecision(
            String requestId,
            String sessionId,
            int turnIndex,
            String callId,
            String userId,
            String assistantId,
            SkillUpdateProposalDto proposal,
            Consumer<SkillUpdateProposalDto> emitProposal) {
        CompletableFuture<SkillUpdateDecision> future = new CompletableFuture<>();
        CompletableFuture<SkillUpdateDecision> previous = pending.put(requestId, future);
        if (previous != null) {
            previous.completeExceptionally(new CancellationException("Superseded by new skill update proposal"));
        }
        pendingMeta.put(requestId, new PendingMeta(
                requestId,
                sessionId,
                turnIndex,
                callId,
                userId,
                assistantId,
                proposal.skillId(),
                proposal.skillName(),
                proposal.filePath(),
                proposal.summary(),
                proposal.feedbackQuote(),
                proposal.currentContent(),
                proposal.proposedContent()));
        try {
            emitProposal.accept(proposal);
            return future.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new RuntimeException(
                    "Timed out after " + timeoutMinutes + " minutes waiting for skill update approval", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Skill update approval interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Skill update approval failed", cause);
        } finally {
            pending.remove(requestId);
            pendingMeta.remove(requestId);
        }
    }

    public boolean submitDecision(String requestId, String decidedBy, SkillUpdateDecision decision) {
        if (requestId == null || requestId.isBlank() || decision == null || decidedBy == null) {
            return false;
        }
        CompletableFuture<SkillUpdateDecision> future = pending.get(requestId);
        if (future == null) {
            return false;
        }
        PendingMeta meta = pendingMeta.get(requestId);
        if (meta == null) {
            return false;
        }
        if (decision.approved()) {
            try {
                skillService.updateFileContent(meta.skillId(), meta.filePath(), meta.proposedContent());
            } catch (RuntimeException e) {
                log.warn("Skill update failed for skill {} path {}: {}",
                        meta.skillId(), meta.filePath(), e.getMessage());
                future.completeExceptionally(e);
                return true;
            }
        }
        persistRevision(meta, decidedBy, decision);
        return future.complete(decision);
    }

    public void cancel(String requestId) {
        CompletableFuture<SkillUpdateDecision> future = pending.remove(requestId);
        pendingMeta.remove(requestId);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new CancellationException("Chat stream ended"));
        }
    }

    private void persistRevision(PendingMeta meta, String decidedBy, SkillUpdateDecision decision) {
        try {
            long now = Instant.now().getEpochSecond();
            revisionRepository.create(AgentSkillRevision.builder()
                    .skillId(meta.skillId())
                    .assistantId(meta.assistantId())
                    .filePath(meta.filePath())
                    .summary(meta.summary())
                    .feedbackQuote(meta.feedbackQuote())
                    .approved(decision.approved())
                    .decidedBy(decidedBy)
                    .sessionId(meta.sessionId())
                    .requestId(meta.requestId())
                    .build(), now);
        } catch (RuntimeException e) {
            log.warn("Failed to persist skill revision for skill {}: {}", meta.skillId(), e.getMessage());
        }
    }
}
