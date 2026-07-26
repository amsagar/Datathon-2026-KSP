package com.ksp.agent.tool.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolGroupDto {
    private String id;
    private String assistantId;
    private String name;
    private String description;
    private String sourceType;
    private boolean enabled;
    private Long createdAt;
    private Long updatedAt;
}
