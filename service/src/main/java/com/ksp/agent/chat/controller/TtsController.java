package com.ksp.agent.chat.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.chat.dto.request.TtsRequest;
import com.ksp.agent.chat.service.IndianTtsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.CHAT_PATH)
public class TtsController {

    private final IndianTtsService ttsService;

    public TtsController(IndianTtsService ttsService) {
        this.ttsService = ttsService;
    }

    /**
     * Synthesize speech with Indian voices: {@code en} → Neerja (en-IN), {@code kn} → Sapna (kn-IN).
     */
    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> speak(@RequestBody TtsRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String lang = request.getLang() == null ? "en" : request.getLang().trim();
        if (!"en".equalsIgnoreCase(lang) && !"kn".equalsIgnoreCase(lang)) {
            return ResponseEntity.badRequest().build();
        }
        String normalized = "kn".equalsIgnoreCase(lang) ? "kn" : "en";
        try {
            byte[] audio = ttsService.synthesize(request.getText(), normalized);
            if (audio.length == 0) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(audio);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
