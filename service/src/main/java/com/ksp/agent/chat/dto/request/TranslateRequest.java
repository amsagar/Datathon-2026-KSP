package com.ksp.agent.chat.dto.request;

public class TranslateRequest {

    private String text;
    /** Target language: {@code en} or {@code kn}. */
    private String targetLang;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public void setTargetLang(String targetLang) {
        this.targetLang = targetLang;
    }
}
