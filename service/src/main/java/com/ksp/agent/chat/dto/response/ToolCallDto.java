package com.ksp.agent.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolCallDto {
    private String id;
    private String name;
    private String input;
    private String output;
    private boolean error;
}
