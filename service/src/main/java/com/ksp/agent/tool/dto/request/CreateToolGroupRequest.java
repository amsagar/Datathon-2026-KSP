package com.ksp.agent.tool.dto.request;

import lombok.Data;

@Data
public class CreateToolGroupRequest {
    private String name;
    private String description;
    private Boolean enabled;
}
