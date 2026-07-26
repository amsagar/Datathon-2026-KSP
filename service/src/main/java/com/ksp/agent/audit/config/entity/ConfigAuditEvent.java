package com.ksp.agent.audit.config.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigAuditEvent {
    private long id;
    private String resourceType;
    private String resourceId;
    private String assistantId;
    private String resourceName;
    private String action;
    private String actor;
    private String summary;
    private long createdAt;
}
