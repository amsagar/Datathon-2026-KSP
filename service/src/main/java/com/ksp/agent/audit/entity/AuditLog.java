package com.ksp.agent.audit.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditLog {
    private String id;
    private String actor;
    private String action;
    private String target;
    private String details;
    private Long createdAt;
}
