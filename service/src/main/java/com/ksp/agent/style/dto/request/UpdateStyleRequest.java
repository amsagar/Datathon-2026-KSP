package com.ksp.agent.style.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateStyleRequest {
    private String name;
    private String description;
    private String instructions;
}
