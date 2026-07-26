package com.ksp.agent.document.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentDto {
    private String id;
    private String assistantId;
    private String name;
    private int chunkCount;
    private boolean enabled;
    private Long createdAt;
    private Long updatedAt;
    /** Zoho-assigned document id after manual upload via the QuickML console; null until set. */
    private String zohoDocumentId;
}
