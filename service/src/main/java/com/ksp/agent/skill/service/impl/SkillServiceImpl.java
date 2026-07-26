package com.ksp.agent.skill.service.impl;

import tools.jackson.databind.JsonNode;
import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.assistant.service.AssistantService;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.dto.RevisionDto;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.skill.dto.request.UpdateSkillRequest;
import com.ksp.agent.skill.dto.response.SkillDto;
import com.ksp.agent.skill.dto.response.SkillFileContentDto;
import com.ksp.agent.skill.dto.response.SkillFileNodeDto;
import com.ksp.agent.skill.entity.AgentSkill;
import com.ksp.agent.skill.repo.AgentSkillRepository;
import com.ksp.agent.skill.service.SkillBundleParser;
import com.ksp.agent.skill.service.SkillBundleParser.ParsedSkill;
import com.ksp.agent.skill.service.SkillService;
import com.ksp.agent.skill.storage.SkillBlobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class SkillServiceImpl implements SkillService {

    private static final String MANIFEST = "SKILL.md";

    private final AgentSkillRepository repository;
    private final AssistantService assistantService;
    private final SkillBlobStore blobStore;
    private final SkillBundleParser parser;
    private final ConfigAuditService configAuditService;

    public SkillServiceImpl(AgentSkillRepository repository,
                            AssistantService assistantService,
                            SkillBlobStore blobStore,
                            SkillBundleParser parser,
                            ConfigAuditService configAuditService) {
        this.repository = repository;
        this.assistantService = assistantService;
        this.blobStore = blobStore;
        this.parser = parser;
        this.configAuditService = configAuditService;
    }

    @Override
    public List<SkillDto> list(String assistantId) {
        return repository.findByAssistant(assistantId).stream().map(this::toDto).toList();
    }

    @Override
    public SkillDto get(String id) {
        return toDto(requireEntity(id));
    }

    @Override
    public SkillDto create(String assistantId, MultipartFile file) {
        assistantService.requireEntity(assistantId); // 404 if assistant is unknown
        requireBlob();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A SKILL.md or .zip file is required.");
        }
        ParsedSkill parsed = parse(file);
        String name = firstNonBlank(parsed.name(), stripExtension(file.getOriginalFilename()), "Untitled skill");
        String prefix = UUID.randomUUID() + "/";
        uploadAll(prefix, parsed.files());

        long now = Instant.now().getEpochSecond();
        AgentSkill skill = new AgentSkill();
        skill.setAssistantId(assistantId);
        skill.setName(name);
        skill.setDescription(parsed.description());
        skill.setBlobPrefix(prefix);
        skill.setEnabled(true);
        String id = repository.create(skill, now);
        skill.setId(id);
        log.info("Created skill {} ({}) for assistant {}", id, name, assistantId);
        archiveAndRecord(skill, AuditAction.create, ConfigAuditService.summarize(AuditAction.create, "skill", name));
        return get(id);
    }

    @Override
    public SkillDto update(String id, UpdateSkillRequest request, MultipartFile file) {
        AgentSkill existing = requireEntity(id);
        long now = Instant.now().getEpochSecond();

        if (file != null && !file.isEmpty()) {
            requireBlob();
            ParsedSkill parsed = parse(file);
            // Write the new bundle under a fresh prefix instead of re-uploading into the existing
            // one. On hierarchical-namespace (ADLS Gen2) accounts, deleting then re-uploading into
            // the same prefix fails with 409 DirectoryIsNotEmpty because empty directory markers
            // survive the per-blob delete. A clean prefix never collides; the old one is pruned
            // best-effort afterwards so a stubborn marker can't abort the upload.
            String oldPrefix = existing.getBlobPrefix();
            String newPrefix = UUID.randomUUID() + "/";
            uploadAll(newPrefix, parsed.files());
            existing.setBlobPrefix(newPrefix);
            try {
                blobStore.deletePrefix(oldPrefix);
            } catch (RuntimeException e) {
                log.warn("Uploaded skill {} to new prefix {} but failed to remove old blobs under {}: {}",
                        id, newPrefix, oldPrefix, e.getMessage());
            }
            // The manifest is the source of truth; refresh metadata from frontmatter on re-upload
            // unless the request explicitly overrides it below.
            if (parsed.name() != null && !parsed.name().isBlank()) {
                existing.setName(parsed.name());
            }
            existing.setDescription(parsed.description());
        }

        if (request != null) {
            if (request.getName() != null && !request.getName().isBlank()) {
                existing.setName(request.getName().trim());
            }
            if (request.getDescription() != null) {
                existing.setDescription(request.getDescription());
            }
            if (request.getEnabled() != null) {
                existing.setEnabled(request.getEnabled());
            }
        }

        repository.update(existing, now);
        archiveAndRecord(existing, AuditAction.update,
                ConfigAuditService.summarize(AuditAction.update, "skill", existing.getName()));
        return get(id);
    }

    @Override
    public List<SkillFileNodeDto> listFiles(String id) {
        AgentSkill skill = requireEntity(id);
        if (!blobStore.isConfigured()) {
            return List.of();
        }
        String prefix = skill.getBlobPrefix();
        List<String> relativePaths = blobStore.list(prefix).stream()
                .map(name -> name.substring(prefix.length()))
                .filter(path -> !path.isBlank() && !path.endsWith("/"))
                .sorted()
                .toList();
        return buildFileTree(relativePaths);
    }

    @Override
    public SkillFileContentDto getFileContent(String id, String path) {
        AgentSkill skill = requireEntity(id);
        requireBlob();
        String relative = validateRelativePath(path);
        String blobName = skill.getBlobPrefix() + relative;
        byte[] data = blobStore.download(blobName);
        return SkillFileContentDto.builder()
                .path(relative)
                .content(new String(data, StandardCharsets.UTF_8))
                .build();
    }

    @Override
    public SkillDto updateFileContent(String id, String path, String content) {
        if (content == null) {
            throw new IllegalArgumentException("Content is required.");
        }
        AgentSkill skill = requireEntity(id);
        requireBlob();
        String relative = validateRelativePath(path);
        blobStore.upload(skill.getBlobPrefix() + relative, content.getBytes(StandardCharsets.UTF_8));

        if (MANIFEST.equals(relative)) {
            SkillBundleParser.SkillFrontmatter fm = parser.readFrontmatter(content);
            if (fm.name() != null && !fm.name().isBlank()) {
                skill.setName(fm.name().trim());
            }
            if (fm.description() != null) {
                skill.setDescription(fm.description());
            }
        }

        long now = Instant.now().getEpochSecond();
        repository.update(skill, now);
        log.info("Updated skill file {} at {}", id, relative);
        archiveAndRecord(skill, AuditAction.file_edit,
                "Edited " + relative + " in skill " + skill.getName());
        return get(id);
    }

    @Override
    public byte[] downloadBundle(String id) {
        AgentSkill skill = requireEntity(id);
        requireBlob();
        String prefix = skill.getBlobPrefix();
        List<String> blobNames = blobStore.list(prefix).stream()
                .map(name -> name.substring(prefix.length()))
                .filter(path -> !path.isBlank() && !path.endsWith("/"))
                .sorted()
                .toList();
        if (blobNames.isEmpty()) {
            throw new IllegalArgumentException("Skill has no files to download.");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String relative : blobNames) {
                byte[] data = blobStore.download(prefix + relative);
                ZipEntry entry = new ZipEntry(relative);
                zos.putNextEntry(entry);
                zos.write(data);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not build skill download archive: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    @Override
    public void delete(String id) {
        AgentSkill skill = requireEntity(id);
        repository.delete(id);
        try {
            if (blobStore.isConfigured()) {
                blobStore.deletePrefix(skill.getBlobPrefix());
            }
        } catch (RuntimeException e) {
            log.warn("Deleted skill {} row but failed to remove blobs under {}: {}",
                    id, skill.getBlobPrefix(), e.getMessage());
        }
        log.info("Deleted skill {}", id);
        configAuditService.recordEvent(ResourceType.skill, id, skill.getAssistantId(), skill.getName(),
                AuditAction.delete, ConfigAuditService.summarize(AuditAction.delete, "skill", skill.getName()));
    }

    @Override
    public List<AgentSkill> forAssistant(String assistantId) {
        return repository.findEnabledByAssistant(assistantId);
    }

    @Override
    public SkillDto revertToVersion(String id, RevisionDto revision) {
        AgentSkill skill = requireEntity(id);
        requireBlob();
        String archivedPrefix = revision.contentRef();
        if (archivedPrefix == null || archivedPrefix.isBlank()) {
            throw new IllegalArgumentException("Revision v" + revision.version() + " has no archived files to restore");
        }
        String oldPrefix = skill.getBlobPrefix();
        String newPrefix = UUID.randomUUID() + "/";
        blobStore.copyPrefix(archivedPrefix, newPrefix);
        skill.setBlobPrefix(newPrefix);

        JsonNode snapshot = revision.snapshot();
        if (snapshot != null) {
            if (snapshot.hasNonNull("name") && !snapshot.get("name").asText().isBlank()) {
                skill.setName(snapshot.get("name").asText());
            }
            if (snapshot.has("description")) {
                skill.setDescription(snapshot.get("description").isNull() ? null : snapshot.get("description").asText());
            }
            if (snapshot.hasNonNull("enabled")) {
                skill.setEnabled(snapshot.get("enabled").asBoolean());
            }
        }

        long now = Instant.now().getEpochSecond();
        repository.update(skill, now);
        try {
            blobStore.deletePrefix(oldPrefix);
        } catch (RuntimeException e) {
            log.warn("Reverted skill {} to new prefix {} but failed to remove old blobs under {}: {}",
                    id, newPrefix, oldPrefix, e.getMessage());
        }
        log.info("Reverted skill {} to version {}", id, revision.version());
        archiveAndRecord(skill, AuditAction.revert, "Reverted to version " + revision.version());
        return get(id);
    }

    /**
     * Copies the skill's current live blob prefix into a new archive prefix under
     * {@code skill-versions/<skillId>/<uuid>/} and records the current state as a new
     * {@code config_revision} snapshot. Called at the end of {@link #create}, {@link #update},
     * {@link #updateFileContent}, and {@link #revertToVersion} — every mutation that changes a
     * skill's live content gets its own archived, revertible snapshot.
     */
    private void archiveAndRecord(AgentSkill skill, AuditAction action, String summary) {
        try {
            String archivePrefix = "skill-versions/" + skill.getId() + "/" + UUID.randomUUID() + "/";
            if (blobStore.isConfigured()) {
                blobStore.copyPrefix(skill.getBlobPrefix(), archivePrefix);
            }
            SkillDto snapshot = toDto(skill);
            configAuditService.recordRevision(ResourceType.skill, skill.getId(), skill.getAssistantId(),
                    skill.getName(), action, snapshot, archivePrefix, summary);
        } catch (Exception e) {
            log.warn("Failed to archive/record revision for skill {}: {}", skill.getId(), e.getMessage());
        }
    }

    private AgentSkill requireEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found: " + id));
    }

    private void requireBlob() {
        if (!blobStore.isConfigured()) {
            throw new IllegalArgumentException(
                    "Skill storage is not configured. Set agent.storage.root to manage skills.");
        }
    }

    private ParsedSkill parse(MultipartFile file) {
        try {
            return parser.parse(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage(), e);
        }
    }

    private void uploadAll(String prefix, Map<String, byte[]> files) {
        files.forEach((relativePath, data) -> blobStore.upload(prefix + relativePath, data));
    }

    private SkillDto toDto(AgentSkill s) {
        return SkillDto.builder()
                .id(s.getId())
                .assistantId(s.getAssistantId())
                .name(s.getName())
                .description(s.getDescription())
                .enabled(s.isEnabled())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "Untitled skill";
    }

    private static String stripExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String validateRelativePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path is required.");
        }
        String normalized = path.replace('\\', '/').stripLeading();
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
        return normalized;
    }

    private static final class TreeNode {
        String name;
        String path = "";
        boolean file;
        final Map<String, TreeNode> children = new TreeMap<>();
    }

    private static List<SkillFileNodeDto> buildFileTree(List<String> filePaths) {
        TreeNode root = new TreeNode();
        for (String filePath : filePaths) {
            String[] parts = filePath.split("/");
            TreeNode current = root;
            for (int i = 0; i < parts.length; i++) {
                boolean isFile = i == parts.length - 1;
                TreeNode next = current.children.computeIfAbsent(parts[i], key -> {
                    TreeNode n = new TreeNode();
                    n.name = key;
                    return n;
                });
                if (isFile) {
                    next.file = true;
                    next.path = filePath;
                } else {
                    next.file = false;
                    next.path = String.join("/", java.util.Arrays.copyOfRange(parts, 0, i + 1));
                }
                current = next;
            }
        }
        return toFileNodes(root.children);
    }

    private static List<SkillFileNodeDto> toFileNodes(Map<String, TreeNode> nodes) {
        List<SkillFileNodeDto> list = new ArrayList<>();
        for (TreeNode node : nodes.values()) {
            if (!node.children.isEmpty()) {
                list.add(SkillFileNodeDto.builder()
                        .path(node.path)
                        .name(node.name)
                        .type("folder")
                        .children(toFileNodes(node.children))
                        .build());
            } else if (node.file) {
                list.add(SkillFileNodeDto.builder()
                        .path(node.path)
                        .name(node.name)
                        .type("file")
                        .children(List.of())
                        .build());
            } else {
                list.add(SkillFileNodeDto.builder()
                        .path(node.path)
                        .name(node.name)
                        .type("folder")
                        .children(toFileNodes(node.children))
                        .build());
            }
        }
        list.sort(Comparator.comparing(SkillFileNodeDto::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }
}
