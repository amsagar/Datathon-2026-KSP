package com.ksp.agent.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageDto {
    private String role;
    private String content;
    private List<ToolCallDto> tools;

    public ChatMessageDto(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
