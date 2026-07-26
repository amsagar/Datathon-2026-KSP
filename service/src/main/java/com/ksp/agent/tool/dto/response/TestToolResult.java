package com.ksp.agent.tool.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TestToolResult {
    private boolean success;
    private String output;
}
