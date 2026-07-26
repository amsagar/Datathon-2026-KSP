package com.ksp.agent.chat.clarification;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.CHAT_PATH)
@CrossOrigin(origins = "*")
public class ChatClarificationController {

    private final WebQuestionBridge webQuestionBridge;

    public ChatClarificationController(WebQuestionBridge webQuestionBridge) {
        this.webQuestionBridge = webQuestionBridge;
    }

    @PostMapping("/clarifications")
    public ResponseEntity<Void> submit(@RequestBody SubmitClarificationRequest body) {
        if (body == null || body.requestId() == null || body.requestId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (body.answers() == null || body.answers().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!webQuestionBridge.submitAnswers(body.requestId(), body.answers())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().build();
    }
}
