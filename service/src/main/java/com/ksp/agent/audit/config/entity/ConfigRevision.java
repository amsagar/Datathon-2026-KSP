package com.ksp.agent.audit.config.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigRevision {
    private long id;
    private String resourceType;
    private String resourceId;
    private String assistantId;
    private int version;
    private String action;
    private String actor;
    private JsonNode snapshot;
    private String contentRef;
    private String summary;
    private long createdAt;
}
