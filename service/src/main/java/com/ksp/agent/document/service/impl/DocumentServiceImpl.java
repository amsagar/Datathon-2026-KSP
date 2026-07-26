package com.ksp.agent.document.service.impl;

import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.document.dto.request.UpdateDocumentRequest;
import com.ksp.agent.document.dto.response.DocumentDto;
import com.ksp.agent.document.entity.AgentDocument;
import com.ksp.agent.document.repo.AgentDocumentRepository;
import com.ksp.agent.document.rag.QuickMlRagService;
import com.ksp.agent.document.service.DocumentService;
import com.ksp.agent.document.storage.DocumentBlobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Per-assistant RAG documents. On upload the raw file is kept in blob storage (the durable
 * source-of-truth). QuickML's knowledge base has no upload API, so it is NOT auto-ingested here —
 * an admin separately uploads the same file via the Zoho console (Generative AI -&gt; Knowledge
 * Base -&gt; Add Documents) and records the resulting Zoho document id via {@link #update} so
 * {@code QuickMlRagService.answer(...)} knows which documents to search.
 */
@Service
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final AgentDocumentRepository repository;
    private final AssistantService assistantService;
    private final DocumentBlobStore blobStore;
    private final QuickMlRagService quickMlRagService;
    private final ConfigAuditService configAuditService;

    public DocumentServiceImpl(AgentDocumentRepository repository,
                               AssistantService assistantService,
                               DocumentBlobStore blobStore,
                               QuickMlRagService quickMlRagService,
                               ConfigAuditService configAuditService) {
        this.repository = repository;
        this.assistantService = assistantService;
        this.blobStore = blobStore;
        this.quickMlRagService = quickMlRagService;
        this.configAuditService = configAuditService;
    }

    @Override
    public List<DocumentDto> list(String assistantId) {
        return repository.findByAssistant(assistantId).stream().map(this::toDto).toList();
    }

    @Override
    public DocumentDto get(String id) {
        return toDto(requireEntity(id));
    }

    @Override
    public DocumentDto create(String assistantId, MultipartFile file) {
        assistantService.requireEntity(assistantId); // 404 if assistant is unknown
        requireBlob();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A document file (.txt, .md, .pdf, .docx) is required.");
        }
        String filename = firstNonBlank(file.getOriginalFilename(), "document");
        String prefix = UUID.randomUUID() + "/";
        byte[] data = bytes(file);

        // Store the raw upload (durable source-of-truth). QuickML's knowledge base has no upload
        // API, so it is NOT auto-ingested — an admin uploads the same file via the Zoho console and
        // records the resulting Zoho document id afterwards via update().
        blobStore.upload(prefix + filename, data);

        long now = Instant.now().getEpochSecond();
        AgentDocument doc = new AgentDocument();
        doc.setAssistantId(assistantId);
        doc.setName(filename);
        doc.setBlobPrefix(prefix);
        doc.setChunkCount(0);
        doc.setEnabled(true);
        String id = repository.create(doc, now);

        log.info("Created document {} ({}) for assistant {}; upload to the QuickML console and "
                + "link its Zoho document id to make it searchable", id, filename, assistantId);
        configAuditService.recordEvent(ResourceType.document, id, assistantId, filename, AuditAction.create,
                ConfigAuditService.summarize(AuditAction.create, "document", filename));
        return get(id);
    }

    @Override
    public DocumentDto update(String id, UpdateDocumentRequest request) {
        AgentDocument existing = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        if (request.getName() != null && !request.getName().isBlank()) {
            existing.setName(request.getName().trim());
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        if (request.getZohoDocumentId() != null) {
            existing.setZohoDocumentId(request.getZohoDocumentId().isBlank() ? null : request.getZohoDocumentId().trim());
        }
        repository.update(existing, now);
        AuditAction action = request.getEnabled() != null
                ? ConfigAuditService.toggleAction(request.getEnabled()) : AuditAction.update;
        configAuditService.recordEvent(ResourceType.document, id, existing.getAssistantId(), existing.getName(),
                action, ConfigAuditService.summarize(action, "document", existing.getName()));
        return get(id);
    }

    @Override
    public void delete(String id) {
        AgentDocument doc = requireEntity(id);
        // QuickML's knowledge base has no delete API; remove it manually in the Zoho console if
        // needed. Delete the row, then the blob by its exact known key (best-effort) — Stratus has
        // no confirmed listing API, so this uses delete(exactKey) rather than deletePrefix, which
        // would silently no-op there.
        repository.delete(id);
        try {
            if (blobStore.isConfigured()) {
                blobStore.delete(doc.getBlobPrefix() + doc.getName());
            }
        } catch (RuntimeException e) {
            log.warn("Deleted document {} row but failed to remove blob {}{}: {}",
                    id, doc.getBlobPrefix(), doc.getName(), e.getMessage());
        }
        log.info("Deleted document {}", id);
        configAuditService.recordEvent(ResourceType.document, id, doc.getAssistantId(), doc.getName(),
                AuditAction.delete, ConfigAuditService.summarize(AuditAction.delete, "document", doc.getName()));
    }

    @Override
    public int enabledCount(String assistantId) {
        return repository.countEnabledByAssistant(assistantId);
    }

    @Override
    public void purgeForAssistant(String assistantId) {
        quickMlRagService.purgeAssistant(assistantId);
    }

    private AgentDocument requireEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private void requireBlob() {
        if (!blobStore.isConfigured()) {
            throw new IllegalArgumentException(
                    "Document storage is not configured. Set agent.storage.root to manage documents.");
        }
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }
    }

    private DocumentDto toDto(AgentDocument d) {
        return DocumentDto.builder()
                .id(d.getId())
                .assistantId(d.getAssistantId())
                .name(d.getName())
                .chunkCount(d.getChunkCount())
                .enabled(d.isEnabled())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .zohoDocumentId(d.getZohoDocumentId())
                .build();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "document";
    }
}
