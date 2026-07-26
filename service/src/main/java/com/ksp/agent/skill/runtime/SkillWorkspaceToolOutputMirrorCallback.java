package com.ksp.agent.skill.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic, policy-free mirror: when a skill workspace is active, writes a tool's raw JSON result to
 * {@code <skillBase>/.skill_io/<toolName>.json} so a skill script can read it from a predictable path
 * instead of the model having to re-serialize the payload through the {@code Write} tool.
 *
 * <p>This is deliberately <em>not</em> coupled to any tool or skill. It applies uniformly to every
 * wrapped tool and names the file after the tool, so a skill that cares (e.g. one whose script reads
 * {@code .skill_io/Get_OrderID.json}) opts in by reading that path, while every other assistant is
 * unaffected — the file is simply written into a per-turn temp workspace that is deleted at turn end.
 *
 * <p>Why this exists: large tool payloads (e.g. an order with a big {@code Lines} array) get truncated
 * when the model copies them into {@code Write}, producing silently-wrong results. Mirroring sends the
 * payload API → disk without passing through the model's token output, so nothing can be dropped.
 *
 * <p>Transparent and best-effort: the delegate's result is always returned unchanged, only
 * JSON-looking results are mirrored, and any write failure is logged and swallowed.
 */
@Slf4j
public class SkillWorkspaceToolOutputMirrorCallback implements ToolCallback {

    public static final String SKILL_WORKSPACE = SkillWorkspaceShellToolCallback.SKILL_WORKSPACE;

    /** Subfolder under the skill base where tool outputs are mirrored. */
    static final String MIRROR_DIR = ".skill_io";

    private final ToolCallback delegate;

    public SkillWorkspaceToolOutputMirrorCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        mirrorQuietly(result, toolContext);
        return result;
    }

    private void mirrorQuietly(String result, ToolContext toolContext) {
        try {
            if (result == null || toolContext == null) {
                return;
            }
            String trimmed = result.trim();
            // Only mirror structured (JSON) results; skip plain-text / error strings.
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                return;
            }
            Object workspace = toolContext.getContext().get(SKILL_WORKSPACE);
            if (workspace == null || String.valueOf(workspace).isBlank()) {
                return;
            }
            String fileName = safeFileName(delegate.getToolDefinition().name());
            if (fileName == null) {
                return;
            }
            // Write into every skill folder's .skill_io (and the workspace root). With more than one
            // skill materialized, a single "base dir" is ambiguous/non-deterministic, so a script that
            // reads <itsOwnSkill>/.skill_io/<tool>.json could miss a file written to a sibling skill.
            // Mirroring to all of them makes the read location-independent.
            List<Path> bases = SkillWorkspacePaths.resolveAllSkillBaseDirs(Path.of(String.valueOf(workspace)));
            List<String> targets = new ArrayList<>();
            for (Path base : bases) {
                try {
                    Path dir = base.resolve(MIRROR_DIR);
                    Files.createDirectories(dir);
                    writeAtomic(dir.resolve(fileName + ".json"), trimmed);
                    targets.add(dir.toString());
                } catch (Exception perDir) {
                    log.warn("Could not mirror tool output '{}' to {}: {}", fileName, base, perDir.toString());
                }
            }
            if (targets.isEmpty()) {
                // A JSON result was produced inside an active skill workspace but landed nowhere. A
                // skill script that reads .skill_io/<tool>.json will then fail with FileNotFoundError,
                // so surface this at WARN instead of hiding it — this is the signal that the mirror
                // silently did not happen.
                log.warn("Tool output '{}' was NOT mirrored to any skill dir (workspace={}); a skill "
                                + "script reading .skill_io/{}.json will fail. Bases tried: {}",
                        fileName, workspace, fileName, bases);
            } else {
                log.info("Mirrored tool output '{}' to {} skill dir(s) ({} bytes): {}",
                        fileName, targets.size(), trimmed.length(), targets);
            }
        } catch (Exception e) {
            // Never break the tool call over a mirroring failure.
            log.warn("Failed to mirror tool output: {}", e.toString());
        }
    }

    /**
     * Durably write via a temp file in the same directory + atomic rename, so a concurrently-running
     * skill script can never observe a half-written (truncated) mirror file — it sees either no file
     * or the complete one. Truncated reads were a source of silently-wrong validation results. Falls
     * back to a plain replace if the filesystem does not support atomic moves.
     */
    private static void writeAtomic(Path target, String content) throws IOException {
        Path dir = target.getParent();
        Path tmp = Files.createTempFile(dir, ".mirror-", ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Keep only filesystem-safe characters; reject empty/odd names rather than guess. */
    private static String safeFileName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        String cleaned = toolName.replaceAll("[^A-Za-z0-9_.-]", "_");
        return cleaned.isBlank() ? null : cleaned;
    }
}
