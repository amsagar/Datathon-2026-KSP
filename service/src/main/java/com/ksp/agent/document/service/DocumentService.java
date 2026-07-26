package com.ksp.agent.document.service;

import com.ksp.agent.document.dto.request.UpdateDocumentRequest;
import com.ksp.agent.document.dto.response.DocumentDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    List<DocumentDto> list(String assistantId);

    DocumentDto get(String id);

    DocumentDto create(String assistantId, MultipartFile file);

    DocumentDto update(String id, UpdateDocumentRequest request);

    void delete(String id);

    /** Number of enabled documents for an assistant (used to gate the RAG advisor). */
    int enabledCount(String assistantId);

    /** Remove all RAG chunks for an assistant from the vector store (called on assistant delete). */
    void purgeForAssistant(String assistantId);
}
