package com.ksp.agent.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateSessionRequest {
    private String title;
    private Boolean archived;
    private String styleId;
    /** Blank clears the pinned provider; a value pins it (mirrors styleId). */
    private String providerId;
}
