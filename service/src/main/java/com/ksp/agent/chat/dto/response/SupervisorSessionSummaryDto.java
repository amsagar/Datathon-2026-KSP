package com.ksp.agent.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row in the supervisor/admin cross-user session listing — enough to identify and open a
 * session ({@link com.ksp.agent.chat.service.ChatSessionService#messages}), not the full detail a
 * session's own DTO carries.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupervisorSessionSummaryDto {
    private String id;
    private String userId;
    private String title;
    private Long updatedAt;
    private boolean archived;
    private boolean temporary;
}
