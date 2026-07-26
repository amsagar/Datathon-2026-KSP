package com.ksp.agent.chat.skillupdate;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.auth.service.SecurityContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.CHAT_PATH)
@CrossOrigin(origins = "*")
public class SkillUpdateDecisionController {

    private final SkillUpdateBridge skillUpdateBridge;
    private final SecurityContextService securityContextService;

    public SkillUpdateDecisionController(SkillUpdateBridge skillUpdateBridge,
                                         SecurityContextService securityContextService) {
        this.skillUpdateBridge = skillUpdateBridge;
        this.securityContextService = securityContextService;
    }

    @PostMapping("/skill-update-decisions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> submit(@RequestBody SubmitSkillUpdateDecisionRequest body) {
        if (body == null || body.requestId() == null || body.requestId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String decidedBy = securityContextService.currentUserIdOrThrow();
        SkillUpdateDecision decision = new SkillUpdateDecision(
                body.approved(),
                body.rejectionReason());
        if (!skillUpdateBridge.submitDecision(body.requestId(), decidedBy, decision)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().build();
    }
}
