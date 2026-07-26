package com.ksp.agent.style.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A reusable, global response-style persona. Its {@code instructions} are injected into the chat
 * system prompt to shape the structure/tone of the assistant's replies. Selectable per session.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseStyle {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private String instructions;
    private boolean defaultStyle;
    private Long createdAt;
    private Long updatedAt;
}
