package com.ksp.agent.skill.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Local-filesystem store for skill bundles. Files are kept under
 * {@code <agent.storage.root>/<container>/<skillId>/<relativePath>} (e.g. {@code <id>/SKILL.md},
 * {@code <id>/scripts/run.py}), preserving the blob-style naming used throughout the service.
 */
@Component
@Slf4j
public class SkillBlobStore {

    private final Path root;

    public SkillBlobStore(@Value("${agent.storage.root:./data/blobs}") String storageRoot,
                          @Value("${agent.storage.skills-container:agent-skills}") String containerName) {
        this.root = Path.of(storageRoot, containerName).toAbsolutePath().normalize();
    }

    public boolean isConfigured() {
        return true;
    }

    private Path resolve(String blobName) {
        Path path = root.resolve(blobName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid blob name: " + blobName);
        }
        return path;
    }

    public void upload(String blobName, byte[] data) {
        Path path = resolve(blobName);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store skill file " + blobName, e);
        }
    }

    /** Blob names under the given prefix (e.g. "<skillId>/"). */
    public List<String> list(String prefix) {
        Path dir = resolve(prefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list skill files under " + prefix, e);
        }
    }

    public byte[] download(String blobName) {
        try {
            return Files.readAllBytes(resolve(blobName));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read skill file " + blobName, e);
        }
    }

    /** Copies every file under {@code sourcePrefix} to the same relative path under {@code destPrefix}. */
    public void copyPrefix(String sourcePrefix, String destPrefix) {
        for (String blobName : list(sourcePrefix)) {
            String relative = blobName.substring(sourcePrefix.length());
            upload(destPrefix + relative, download(blobName));
        }
    }

    /** Deletes every file under the given prefix (e.g. "<skillId>/"), deepest-first. */
    public void deletePrefix(String prefix) {
        Path dir = resolve(prefix);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete skill file {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete skill files under " + prefix, e);
        }
    }
}
