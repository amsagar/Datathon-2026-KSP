package com.ksp.agent.style.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResponseStyleDto {
    private String id;
    private String name;
    private String description;
    private String instructions;
    private boolean defaultStyle;
    private Long createdAt;
    private Long updatedAt;
}
