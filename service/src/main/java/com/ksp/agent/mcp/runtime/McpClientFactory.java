package com.ksp.agent.mcp.runtime;

import com.ksp.agent.mcp.auth.McpAuthService;
import com.ksp.agent.mcp.entity.McpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link McpSyncClient}s for remote MCP servers (Streamable HTTP or SSE), injecting the
 * server's resolved auth headers on every request. Also handles one-shot tool discovery.
 */
@Component
@Slf4j
public class McpClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final McpAuthService authService;

    public McpClientFactory(McpAuthService authService) {
        this.authService = authService;
    }

    /** Build and initialize a sync client for the given server. Caller is responsible for closing it. */
    public McpSyncClient buildClient(McpServer server) {
        Map<String, String> headers = authService.authHeaders(server);
        McpClientTransport transport = transportFor(server, headers);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .build();
        client.initialize();
        return client;
    }

    /** Connect, list the server's tools, then close. Used by the discover endpoint. */
    public List<McpSchema.Tool> discover(McpServer server) {
        McpSyncClient client = buildClient(server);
        try {
            return client.listTools().tools();
        } finally {
            closeQuietly(client);
        }
    }

    public static void closeQuietly(McpSyncClient client) {
        if (client == null) {
            return;
        }
        try {
            client.closeGracefully();
        } catch (Exception e) {
            log.debug("Error closing MCP client: {}", e.getMessage());
        }
    }

    private McpClientTransport transportFor(McpServer server, Map<String, String> headers) {
        String[] parts = splitOrigin(server.getUrl());
        String origin = parts[0];
        String path = parts[1];
        String transportType = server.getTransport() == null ? "streamable_http" : server.getTransport().trim();

        if ("sse".equalsIgnoreCase(transportType)) {
            String sseEndpoint = (server.getSseEndpoint() != null && !server.getSseEndpoint().isBlank())
                    ? server.getSseEndpoint().trim()
                    : (path.isBlank() ? "/sse" : path);
            return HttpClientSseClientTransport.builder(origin)
                    .sseEndpoint(sseEndpoint)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .customizeRequest(b -> headers.forEach(b::header))
                    .build();
        }

        // Default: Streamable HTTP.
        String endpoint = path.isBlank() ? "/mcp" : path;
        return HttpClientStreamableHttpTransport.builder(origin)
                .endpoint(endpoint)
                .connectTimeout(CONNECT_TIMEOUT)
                .customizeRequest(b -> headers.forEach(b::header))
                .build();
    }

    /** Split a full URL into [scheme://authority, path(+query)]. Falls back to (url, "") on parse errors. */
    private static String[] splitOrigin(String url) {
        try {
            URI uri = URI.create(url.trim());
            String origin = uri.getScheme() + "://" + uri.getAuthority();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                path = path + "?" + uri.getRawQuery();
            }
            return new String[] { origin, path };
        } catch (Exception e) {
            return new String[] { url, "" };
        }
    }
}
