package com.ksp.agent.document.storage;

import com.ksp.agent.llm.QuickMlTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Store for the raw uploaded RAG documents. Two backends, selected by configuration:
 * <ul>
 *   <li><b>Catalyst Stratus</b> (when {@code agent.stratus.bucket-url} is set): objects are read and
 *       written directly on the bucket's own domain — confirmed empirically:
 *       {@code PUT}/{@code GET}/{@code DELETE <bucket-url>/<url-encoded-key>}, authenticated with
 *       {@code Authorization: Zoho-oauthtoken <token>} + {@code CATALYST-ORG} (the same OAuth
 *       refresh token/service as the LLM and RAG clients).</li>
 *   <li><b>Local filesystem</b> (fallback): files under
 *       {@code <agent.storage.root>/<container>/<documentId>/<originalFilename>}, used for local dev.</li>
 * </ul>
 * The blob-name contract ({@code <documentId>/<originalFilename>}) is identical for both backends, so
 * {@code AgentDocument.blobPrefix} and all callers are unaffected by the choice.
 *
 * <p><b>Known gap:</b> Stratus has no confirmed object-listing REST endpoint (two documented URL
 * patterns both returned {@code INVALID_URL_PATTERN} against a live bucket). {@link #list} therefore
 * returns an empty list when Stratus is active — this only affects the separate agent-skills
 * file-bundle feature (which needs directory listing to reconstruct a multi-file skill), not
 * document RAG (which only needs upload/download/delete by exact key, all confirmed working).
 */
@Component
@Slf4j
public class DocumentBlobStore {

    private final Path root;
    private final StratusProperties stratus;
    private final QuickMlTokenService tokenService;
    private final RestClient stratusClient;

    public DocumentBlobStore(@Value("${agent.storage.root:./data/blobs}") String storageRoot,
                             @Value("${agent.storage.documents-container:agent-documents}") String containerName,
                             StratusProperties stratus,
                             QuickMlTokenService tokenService) {
        this.root = Path.of(storageRoot, containerName).toAbsolutePath().normalize();
        this.stratus = stratus;
        this.tokenService = tokenService;
        this.stratusClient = usingStratus()
                ? RestClient.builder()
                        .baseUrl(stratus.getBucketUrl())
                        .requestInterceptor((request, body, execution) -> {
                            if (tokenService.usesOAuthRefresh()) {
                                request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                                        "Zoho-oauthtoken " + tokenService.getAccessToken());
                                if (stratus.getCatalystOrg() != null && !stratus.getCatalystOrg().isBlank()) {
                                    request.getHeaders().set("CATALYST-ORG", stratus.getCatalystOrg());
                                }
                            } else if (stratus.getApiKey() != null && !stratus.getApiKey().isBlank()) {
                                request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + stratus.getApiKey());
                            }
                            return execution.execute(request, body);
                        })
                        .build()
                : null;
        log.info("DocumentBlobStore backend: {}", usingStratus() ? "Catalyst Stratus" : "local disk (" + root + ")");
    }

    private boolean usingStratus() {
        return stratus != null && stratus.getBucketUrl() != null && !stratus.getBucketUrl().isBlank();
    }

    public boolean isConfigured() {
        return true;
    }

    // ---------------- Stratus (object storage) ----------------

    /** Blob names contain '/' as a path separator; encode each segment, not the separator itself. */
    private static String encodeKey(String blobName) {
        return java.util.Arrays.stream(blobName.split("/", -1))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private void stratusUpload(String blobName, byte[] data) {
        stratusClient.put()
                .uri("/" + encodeKey(blobName))
                .body(data)
                .retrieve()
                .toBodilessEntity();
    }

    private byte[] stratusDownload(String blobName) {
        return stratusClient.get()
                .uri("/" + encodeKey(blobName))
                .retrieve()
                .body(byte[].class);
    }

    private void stratusDelete(String blobName) {
        stratusClient.delete()
                .uri("/" + encodeKey(blobName))
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------- Public API (dispatches to the active backend) ----------------

    public void upload(String blobName, byte[] data) {
        if (usingStratus()) {
            stratusUpload(blobName, data);
            return;
        }
        Path path = resolve(blobName);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store document file " + blobName, e);
        }
    }

    public byte[] download(String blobName) {
        if (usingStratus()) {
            return stratusDownload(blobName);
        }
        try {
            return Files.readAllBytes(resolve(blobName));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read document file " + blobName, e);
        }
    }

    /**
     * Blob names under the given prefix (e.g. "<documentId>/"). Stratus has no confirmed listing
     * endpoint (see class Javadoc); callers that need this under Stratus (agent-skills file bundles)
     * will see an empty list until that gap is resolved.
     */
    public List<String> list(String prefix) {
        if (usingStratus()) {
            log.warn("DocumentBlobStore.list({}) has no confirmed Stratus API; returning empty. "
                    + "This only affects agent-skills file bundles, not document RAG.", prefix);
            return List.of();
        }
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
            throw new UncheckedIOException("Failed to list document files under " + prefix, e);
        }
    }

    /**
     * Deletes a single, exactly-known blob by its full key (e.g. "&lt;documentId&gt;/report.pdf").
     * Prefer this over {@link #deletePrefix} whenever the caller already knows the exact key (as
     * document uploads do) — it works under Stratus without needing the unresolved listing API.
     */
    public void delete(String blobName) {
        if (usingStratus()) {
            stratusDelete(blobName);
            return;
        }
        try {
            Files.deleteIfExists(resolve(blobName));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete document file " + blobName, e);
        }
    }

    /**
     * Deletes every file under the given prefix (e.g. "<documentId>/") by listing first — needs
     * {@link #list}, which has no confirmed Stratus API (see class Javadoc), so this is a no-op
     * under Stratus. Only the agent-skills file-bundle feature relies on this; document deletes use
     * {@link #delete} with the exact known key instead.
     */
    public void deletePrefix(String prefix) {
        if (usingStratus()) {
            for (String key : list(prefix)) {
                stratusDelete(key);
            }
            return;
        }
        Path dir = resolve(prefix);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete document file {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete document files under " + prefix, e);
        }
    }

    private Path resolve(String blobName) {
        Path path = root.resolve(blobName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid blob name: " + blobName);
        }
        return path;
    }

    /** Registers {@link StratusProperties} without a component-scan annotation on the record. */
    @Configuration
    @EnableConfigurationProperties(StratusProperties.class)
    static class StratusPropertiesConfig {
    }
}
