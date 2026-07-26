package com.ksp.agent.tool.imports.dto;

import lombok.Data;

@Data
public class ImportRequest {
    private String content;
    private String host;
    /** OpenAPI spec URL; fetched server-side when {@code content} is absent. */
    private String specUrl;
    /** Optional override for the auto-created import group name. */
    private String groupName;
}
