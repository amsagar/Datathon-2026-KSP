package com.ksp.agent.chat.dto.request;

public class TtsRequest {

    private String text;
    /** Voice language: {@code en} (Indian English) or {@code kn} (Kannada). */
    private String lang;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }
}
