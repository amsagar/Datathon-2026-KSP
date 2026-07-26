package com.ksp.agent.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Viewer-facing snapshot of a shared conversation (read-only, any authenticated user). */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SharedChatDto {
    private String title;
    private String assistantName;
    private List<ChatMessageDto> messages;
    private Long createdAt;
}
