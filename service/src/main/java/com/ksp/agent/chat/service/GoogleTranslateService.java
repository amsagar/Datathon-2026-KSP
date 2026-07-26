package com.ksp.agent.chat.service;

import me.bush.translator.Language;
import me.bush.translator.Translation;
import me.bush.translator.Translator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * EN↔KN translation via the open-source
 * <a href="https://github.com/therealbush/translator">therealbush/translator</a> library
 * (unofficial Google Translate endpoint — no API key).
 */
@Service
public class GoogleTranslateService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTranslateService.class);
    private static final int MAX_CHARS = 4500;

    private final Translator translator = new Translator();

    public String translate(String text, String targetLang) {
        if (text == null || text.isBlank()) {
            return "";
        }
        boolean toKannada = "kn".equalsIgnoreCase(targetLang);
        int kn = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x0C80 && cp <= 0x0CFF) {
                kn++;
            } else if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) {
                latin++;
            }
        }
        // Majority-script skip only — a few Kannada chars in an English reply
        // must not block EN→KN translation of chat messages.
        boolean mostlyKannada = kn > 0 && kn >= latin;
        Language target = toKannada ? Language.KANNADA : Language.ENGLISH;
        if ((toKannada && mostlyKannada) || (!toKannada && !mostlyKannada && kn == 0)) {
            return text.strip();
        }

        try {
            if (text.length() <= MAX_CHARS) {
                return translateOnce(text, target);
            }
            // Google unofficial endpoint has a soft length limit — chunk long posts.
            StringBuilder out = new StringBuilder();
            int i = 0;
            while (i < text.length()) {
                int end = Math.min(i + MAX_CHARS, text.length());
                if (end < text.length()) {
                    int cut = text.lastIndexOf('\n', end);
                    if (cut <= i + MAX_CHARS / 3) {
                        cut = text.lastIndexOf(' ', end);
                    }
                    if (cut > i + MAX_CHARS / 3) {
                        end = cut;
                    }
                }
                String piece = text.substring(i, end).trim();
                if (!piece.isEmpty()) {
                    if (!out.isEmpty()) {
                        out.append('\n');
                    }
                    out.append(translateOnce(piece, target));
                }
                i = Math.max(end, i + 1);
            }
            return out.toString().strip();
        } catch (Exception e) {
            log.warn("Google translate failed: {}", e.getMessage());
            throw new IllegalStateException("Translation failed: " + e.getMessage(), e);
        }
    }

    private String translateOnce(String text, Language target) {
        Translation result = translator.translateBlocking(text, target, Language.AUTO);
        if (result == null || result.getTranslatedText() == null) {
            throw new IllegalStateException("Empty translation response");
        }
        return result.getTranslatedText().strip();
    }
}
