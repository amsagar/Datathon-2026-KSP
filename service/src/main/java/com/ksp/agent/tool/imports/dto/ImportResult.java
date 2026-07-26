package com.ksp.agent.tool.imports.dto;

import com.ksp.agent.tool.dto.response.AgentToolDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ImportResult {
    private int count;
    private List<AgentToolDto> tools;
    private String groupId;
    private String groupName;
}
