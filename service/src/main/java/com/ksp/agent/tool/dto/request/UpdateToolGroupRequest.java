package com.ksp.agent.tool.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateToolGroupRequest {
    private String name;
    private String description;
    private Boolean enabled;
}
