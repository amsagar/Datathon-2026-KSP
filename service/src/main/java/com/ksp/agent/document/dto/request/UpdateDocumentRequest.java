package com.ksp.agent.document.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateDocumentRequest {
    private String name;
    private Boolean enabled;
    /**
     * Zoho document id from the QuickML console (Generative AI -&gt; Knowledge Base) after manually
     * uploading this document's file there — required for {@code QuickMlRagService.answer(...)} to
     * include this document when querying the assistant's knowledge base.
     */
    private String zohoDocumentId;
}
