package com.ksp.agent.chat.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.chat.dto.request.TranslateRequest;
import com.ksp.agent.chat.dto.response.TranslateResponse;
import com.ksp.agent.chat.service.GoogleTranslateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.CHAT_PATH)
public class TranslateController {

    private final GoogleTranslateService translateService;

    public TranslateController(GoogleTranslateService translateService) {
        this.translateService = translateService;
    }

    @PostMapping("/translate")
    public ResponseEntity<?> translate(@RequestBody TranslateRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String target = request.getTargetLang() == null ? "en" : request.getTargetLang().trim();
        if (!"en".equalsIgnoreCase(target) && !"kn".equalsIgnoreCase(target)) {
            return ResponseEntity.badRequest().build();
        }
        String normalized = "kn".equalsIgnoreCase(target) ? "kn" : "en";
        try {
            String translated = translateService.translate(request.getText(), normalized);
            return ResponseEntity.ok(new TranslateResponse(translated, normalized));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
