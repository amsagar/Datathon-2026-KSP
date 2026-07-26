package com.ksp.agent.audit.config.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.dto.ConfigAuditFeedPage;
import com.ksp.agent.audit.config.dto.RevisionDto;
import com.ksp.agent.audit.config.dto.RevisionFileDto;
import com.ksp.agent.audit.config.dto.RevisionSummaryDto;
import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.skill.storage.SkillBlobStore;
import com.ksp.agent.audit.config.dto.AuditAccessSettingsDto;
import com.ksp.agent.audit.config.service.AuditAccessSettingsService;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.audit.config.service.ConfigRevertService;
import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.CONFIG_AUDIT_PATH)
@CrossOrigin(origins = "*")
public class ConfigAuditController {

    private final ConfigAuditService configAuditService;
    private final ConfigRevertService configRevertService;
    private final AuditAccessSettingsService auditAccessSettingsService;
    private final SkillBlobStore skillBlobStore;
    private final SecurityContextService securityContextService;

    public ConfigAuditController(ConfigAuditService configAuditService,
                                 ConfigRevertService configRevertService,
                                 AuditAccessSettingsService auditAccessSettingsService,
                                 SkillBlobStore skillBlobStore,
                                 SecurityContextService securityContextService) {
        this.configAuditService = configAuditService;
        this.configRevertService = configRevertService;
        this.auditAccessSettingsService = auditAccessSettingsService;
        this.skillBlobStore = skillBlobStore;
        this.securityContextService = securityContextService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @auditAccessSettingsService.isNonAdminReadEnabled()")
    public ResponseEntity<ConfigAuditFeedPage> feed(@RequestParam(required = false) String resourceType,
                                                    @RequestParam(required = false) String actor,
                                                    @RequestParam(required = false) String resourceId,
                                                    @RequestParam(required = false) Long from,
                                                    @RequestParam(required = false) Long to,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        ResourceType type = resourceType == null || resourceType.isBlank() ? null : ResourceType.fromString(resourceType);
        return ResponseEntity.ok(configAuditService.feed(type, actor, resourceId, from, to, page, size));
    }

    @GetMapping("/{resourceType}/{resourceId}/revisions")
    @PreAuthorize("hasRole('ADMIN') or @auditAccessSettingsService.isNonAdminReadEnabled()")
    public ResponseEntity<List<RevisionSummaryDto>> revisions(@PathVariable String resourceType,
                                                              @PathVariable String resourceId) {
        ResourceType type = requireVersioned(resourceType);
        return ResponseEntity.ok(configAuditService.revisionSummaries(type, resourceId));
    }

    @GetMapping("/{resourceType}/{resourceId}/revisions/{version}")
    @PreAuthorize("hasRole('ADMIN') or @auditAccessSettingsService.isNonAdminReadEnabled()")
    public ResponseEntity<RevisionDto> revision(@PathVariable String resourceType,
                                                @PathVariable String resourceId,
                                                @PathVariable int version) {
        ResourceType type = requireVersioned(resourceType);
        RevisionDto dto = configAuditService.revision(type, resourceId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Revision v" + version + " not found for " + type + " " + resourceId));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{resourceType}/{resourceId}/revisions/{version}/files")
    @PreAuthorize("hasRole('ADMIN') or @auditAccessSettingsService.isNonAdminReadEnabled()")
    public ResponseEntity<List<RevisionFileDto>> revisionFiles(@PathVariable String resourceType,
                                                               @PathVariable String resourceId,
                                                               @PathVariable int version) {
        ResourceType type = ResourceType.fromString(resourceType);
        if (type != ResourceType.skill) {
            throw new IllegalArgumentException("File diffing is only available for skills");
        }
        RevisionDto revision = configAuditService.revision(type, resourceId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Revision v" + version + " not found for " + type + " " + resourceId));
        String prefix = revision.contentRef();
        if (prefix == null || prefix.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<RevisionFileDto> files = skillBlobStore.list(prefix).stream()
                .map(blobName -> toFileDto(blobName.substring(prefix.length()), skillBlobStore.download(blobName)))
                .toList();
        return ResponseEntity.ok(files);
    }

    @PostMapping("/{resourceType}/{resourceId}/revisions/{version}/revert")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revert(@PathVariable String resourceType,
                                       @PathVariable String resourceId,
                                       @PathVariable int version) {
        ResourceType type = requireVersioned(resourceType);
        configRevertService.revert(type, resourceId, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public ResponseEntity<AuditAccessSettingsDto> getSettings() {
        return ResponseEntity.ok(new AuditAccessSettingsDto(auditAccessSettingsService.isNonAdminReadEnabled()));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditAccessSettingsDto> updateSettings(@RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("nonAdminReadEnabled"));
        auditAccessSettingsService.setNonAdminReadEnabled(enabled, securityContextService.currentUserIdOrThrow());
        return ResponseEntity.ok(new AuditAccessSettingsDto(enabled));
    }

    private static ResourceType requireVersioned(String resourceType) {
        ResourceType type = ResourceType.fromString(resourceType);
        if (!type.isVersioned()) {
            throw new IllegalArgumentException("Resource type " + type + " has no version history");
        }
        return type;
    }

    private static RevisionFileDto toFileDto(String path, byte[] data) {
        try {
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data))
                    .toString();
            return new RevisionFileDto(path, content, false);
        } catch (CharacterCodingException e) {
            return new RevisionFileDto(path, null, true);
        }
    }
}
