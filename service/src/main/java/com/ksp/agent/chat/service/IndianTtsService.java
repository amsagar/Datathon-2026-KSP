package com.ksp.agent.chat.service;

import com.ksp.agent.chat.tts.EdgeTtsClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Speak-aloud via two Indian neural voices (Edge Read Aloud):
 * <ul>
 *   <li>{@code en} → Indian English ({@code en-IN-NeerjaNeural})</li>
 *   <li>{@code kn} → Kannada ({@code kn-IN-SapnaNeural})</li>
 * </ul>
 * Uses an in-repo Edge client with current {@code Sec-MS-GEC} auth (avoids 403 from stale SDKs).
 */
@Service
public class IndianTtsService {

    private static final Logger log = LoggerFactory.getLogger(IndianTtsService.class);
    private static final int CACHE_MAX = 64;

    private final String voiceEn;
    private final String voiceKn;
    private final EdgeTtsClient edgeTtsClient = new EdgeTtsClient();
    private final Map<String, byte[]> audioCache = new LinkedHashMap<>(CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHE_MAX;
        }
    };

    public IndianTtsService(
            @Value("${agent.tts.voice-en:en-IN-NeerjaNeural}") String voiceEn,
            @Value("${agent.tts.voice-kn:kn-IN-SapnaNeural}") String voiceKn) {
        this.voiceEn = voiceEn;
        this.voiceKn = voiceKn;
    }

    @PostConstruct
    void logVoices() {
        log.info("Indian TTS configured: en={}, kn={} (Edge Sec-MS-GEC client)", voiceEn, voiceKn);
    }

    public byte[] synthesize(String text, String lang) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        String cleaned = stripForSpeech(text);
        if (cleaned.isBlank()) {
            return new byte[0];
        }
        boolean kannada = "kn".equalsIgnoreCase(lang);
        String voiceName = kannada ? voiceKn : voiceEn;
        String cacheKey = (kannada ? "kn:" : "en:") + cleaned;
        synchronized (audioCache) {
            byte[] hit = audioCache.get(cacheKey);
            if (hit != null) {
                return hit;
            }
        }

        try {
            byte[] bytes = edgeTtsClient.synthesizeMp3(voiceName, cleaned);
            if (bytes.length == 0) {
                throw new IllegalStateException("Edge TTS returned empty audio");
            }
            synchronized (audioCache) {
                audioCache.put(cacheKey, bytes);
            }
            return bytes;
        } catch (Exception e) {
            log.warn("Indian TTS failed for voice {}: {}", voiceName, e.getMessage());
            throw new IllegalStateException("TTS failed: " + e.getMessage(), e);
        }
    }

    static String stripForSpeech(String markdown) {
        return markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("[*_#`>|]", "")
                .replaceAll("\\[(.*?)]\\(.*?\\)", "$1")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
