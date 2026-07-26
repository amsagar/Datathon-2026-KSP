package com.ksp.agent.skill.runtime;

import com.ksp.agent.assistant.entity.Assistant;
import com.ksp.agent.assistant.repo.AssistantRepository;
import com.ksp.agent.chat.audit.ChatAuditLog;
import com.ksp.agent.skill.entity.AgentSkill;
import com.ksp.agent.skill.platform.PlatformSkill;
import com.ksp.agent.skill.platform.PlatformSkillRegistry;
import com.ksp.agent.skill.service.SkillService;
import com.ksp.agent.skill.storage.SkillBlobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Materializes an assistant's enabled skills into a fresh temporary directory so the
 * {@code SkillsTool}/{@code FileSystemTools}/{@code ShellTools} can act on real files on disk.
 * Two sources are combined: skills uploaded to Blob storage and platform skills bundled in the
 * jar. One workspace is created per chat turn and deleted when the turn ends.
 *
 * <p>Layout: {@code <workspace>/<skillId>/SKILL.md}, {@code <workspace>/<skillId>/scripts/foo.py}, …
 * — each skill lives in its own subfolder so {@code SkillsTool} (which scans subfolders for a
 * {@code SKILL.md}) discovers them all. Platform skills use a {@code platform-} folder prefix so
 * they can never collide with uploaded-skill UUID folders.
 */
@Service
@Slf4j
public class SkillWorkspaceService {

    private final SkillService skillService;
    private final SkillBlobStore blobStore;
    private final PlatformSkillRegistry platformSkillRegistry;
    private final AssistantRepository assistantRepository;

    public SkillWorkspaceService(SkillService skillService,
                                 SkillBlobStore blobStore,
                                 PlatformSkillRegistry platformSkillRegistry,
                                 AssistantRepository assistantRepository) {
        this.skillService = skillService;
        this.blobStore = blobStore;
        this.platformSkillRegistry = platformSkillRegistry;
        this.assistantRepository = assistantRepository;
    }

    /**
     * Writes every active skill for the assistant (platform + uploaded) into a new temp dir and
     * returns its root, or {@code null} when there is nothing to materialize.
     */
    public Path materialize(String assistantId) {
        if (assistantId == null) {
            return null;
        }
        List<PlatformSkill> platformSkills = platformSkillRegistry.activeFor(
                assistantRepository.findById(assistantId).map(Assistant::getPlatformSkills).orElse(null));
        List<AgentSkill> skills = blobStore.isConfigured()
                ? skillService.forAssistant(assistantId) : List.of();
        if (platformSkills.isEmpty() && skills.isEmpty()) {
            return null;
        }

        Path workspace;
        try {
            workspace = Files.createTempDirectory("agent-skills-");
        } catch (IOException e) {
            log.warn("Could not create skill workspace for assistant {}: {}", assistantId, e.getMessage());
            return null;
        }

        int materialized = 0;
        List<String> materializedSkillNames = new ArrayList<>();
        for (PlatformSkill skill : platformSkills) {
            Path skillRoot = workspace.resolve("platform-" + skill.id());
            try {
                for (Map.Entry<String, byte[]> file : skill.files().entrySet()) {
                    Path target = skillRoot.resolve(file.getKey()).normalize();
                    if (!target.startsWith(skillRoot)) {
                        log.warn("Skipping platform skill file {} that escapes skill root", file.getKey());
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    Files.write(target, file.getValue());
                }
                materialized++;
                materializedSkillNames.add(skill.name());
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to materialize platform skill {}: {}", skill.id(), e.getMessage());
            }
        }
        for (AgentSkill skill : skills) {
            String prefix = skill.getBlobPrefix();
            Path skillRoot = workspace.resolve(skill.getId());
            try {
                List<String> blobNames = blobStore.list(prefix);
                for (String blobName : blobNames) {
                    String relative = blobName.substring(prefix.length());
                    if (relative.isBlank()) {
                        continue;
                    }
                    // Skip directory-marker blobs. On hierarchical-namespace (ADLS Gen2) accounts
                    // Azure auto-creates a zero-byte object for each parent folder (e.g. "references"
                    // alongside "references/field-mappings.md"). Writing it as a file would then
                    // collide with creating the real subdirectory and abort the whole skill.
                    if (relative.endsWith("/") || isDirectoryMarker(blobName, blobNames)) {
                        continue;
                    }
                    Path target = skillRoot.resolve(relative).normalize();
                    if (!target.startsWith(skillRoot)) {
                        log.warn("Skipping blob {} that escapes skill root", blobName);
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    Files.write(target, blobStore.download(blobName));
                }
                materialized++;
                materializedSkillNames.add(skill.getName());
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to materialize skill {} ({}): {}", skill.getId(), skill.getName(), e.getMessage());
            }
        }

        if (materialized == 0) {
            cleanup(workspace);
            return null;
        }
        ChatAuditLog.skillMaterialized(assistantId, materialized, workspace.toString(), materializedSkillNames);
        return workspace;
    }

    /**
     * True when {@code blobName} is a folder placeholder — i.e. some other blob in the listing is
     * nested under it ({@code blobName + "/..."}). Such markers must not be written as files.
     */
    private static boolean isDirectoryMarker(String blobName, List<String> allNames) {
        String childPrefix = blobName + "/";
        for (String other : allNames) {
            if (other.startsWith(childPrefix)) {
                return true;
            }
        }
        return false;
    }

    /** Recursively deletes the workspace; safe to call with {@code null}. */
    public void cleanup(Path workspace) {
        if (workspace == null) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up skill workspace {}: {}", workspace, e.getMessage());
        }
    }
}
