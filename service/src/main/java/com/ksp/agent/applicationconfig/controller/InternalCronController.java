package com.ksp.agent.applicationconfig.controller;

import com.ksp.agent.alert.runtime.AlertEvaluationJob;
import com.ksp.agent.chat.runtime.TemporaryChatRetentionJob;
import com.ksp.agent.memory.runtime.MemoryDecayJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints invoked by Catalyst Cron (Cloud Scale) instead of in-process {@code @Scheduled} jobs, so
 * a single external scheduler owns these tasks — important because AppSail may run more than one
 * instance and in-process scheduling would double-fire.
 *
 * <p>Catalyst Cron has no user session, so these are authenticated with a shared secret header
 * ({@code X-Cron-Secret}) matched against {@code agent.cron.secret}; the path is otherwise public in
 * {@code SecurityConfig}. Configure the cron jobs in Catalyst to POST here on the desired schedule
 * (03:00 daily for decay, 03:30 for temporary-chat purge; alert-evaluation is intended to run far
 * more often, e.g. every 15-30 minutes, since early warnings are only useful if fresh).
 */
@RestController
@RequestMapping("/api/v1/internal/cron")
@Slf4j
public class InternalCronController {

    private final MemoryDecayJob memoryDecayJob;
    private final TemporaryChatRetentionJob temporaryChatRetentionJob;
    private final AlertEvaluationJob alertEvaluationJob;
    private final String cronSecret;

    public InternalCronController(MemoryDecayJob memoryDecayJob,
                                  TemporaryChatRetentionJob temporaryChatRetentionJob,
                                  AlertEvaluationJob alertEvaluationJob,
                                  @Value("${agent.cron.secret:}") String cronSecret) {
        this.memoryDecayJob = memoryDecayJob;
        this.temporaryChatRetentionJob = temporaryChatRetentionJob;
        this.alertEvaluationJob = alertEvaluationJob;
        this.cronSecret = cronSecret;
    }

    @PostMapping("/memory-decay")
    public ResponseEntity<String> memoryDecay(@RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        if (unauthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        memoryDecayJob.runDecay();
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/temporary-chat-purge")
    public ResponseEntity<String> temporaryChatPurge(@RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        if (unauthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        temporaryChatRetentionJob.purge();
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/alert-evaluation")
    public ResponseEntity<String> alertEvaluation(@RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        if (unauthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        alertEvaluationJob.evaluate();
        return ResponseEntity.ok("ok");
    }

    private boolean unauthorized(String secret) {
        if (cronSecret == null || cronSecret.isBlank()) {
            log.warn("agent.cron.secret is not set; refusing cron trigger.");
            return true;
        }
        boolean ok = cronSecret.equals(secret);
        if (!ok) {
            log.warn("Rejected cron trigger with missing/invalid X-Cron-Secret.");
        }
        return !ok;
    }
}
