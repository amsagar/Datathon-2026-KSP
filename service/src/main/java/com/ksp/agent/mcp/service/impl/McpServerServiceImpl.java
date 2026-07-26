package com.ksp.agent.mcp.service.impl;

import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.audit.config.AuditAction;
import com.ksp.agent.audit.config.ResourceType;
import com.ksp.agent.audit.config.service.ConfigAuditService;
import com.ksp.agent.mcp.dto.request.CreateMcpServerRequest;
import com.ksp.agent.mcp.dto.request.UpdateMcpServerRequest;
import com.ksp.agent.mcp.dto.response.McpServerDto;
import com.ksp.agent.mcp.dto.response.McpServerToolDto;
import com.ksp.agent.mcp.entity.McpServer;
import com.ksp.agent.mcp.entity.McpServerTool;
import com.ksp.agent.mcp.repo.McpServerRepository;
import com.ksp.agent.mcp.repo.McpServerToolRepository;
import com.ksp.agent.mcp.runtime.McpClientFactory;
import com.ksp.agent.mcp.service.McpServerService;
import com.ksp.agent.tool.auth.service.EncryptionService;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class McpServerServiceImpl implements McpServerService {

    private static final List<String> VALID_TRANSPORTS = List.of("streamable_http", "sse");

    private final McpServerRepository serverRepository;
    private final McpServerToolRepository serverToolRepository;
    private final McpClientFactory clientFactory;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final ConfigAuditService configAuditService;

    public McpServerServiceImpl(McpServerRepository serverRepository,
                                McpServerToolRepository serverToolRepository,
                                McpClientFactory clientFactory,
                                EncryptionService encryptionService,
                                ObjectMapper objectMapper,
                                ConfigAuditService configAuditService) {
        this.serverRepository = serverRepository;
        this.serverToolRepository = serverToolRepository;
        this.clientFactory = clientFactory;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.configAuditService = configAuditService;
    }

    @Override
    public List<McpServerDto> list(String assistantId) {
        return serverRepository.findByAssistant(assistantId).stream()
                .map(s -> toDto(s, null))
                .toList();
    }

    @Override
    public McpServerDto get(String id) {
        McpServer server = requireEntity(id);
        return toDto(server, serverToolRepository.findByServer(id));
    }

    @Override
    public McpServerDto create(String assistantId, CreateMcpServerRequest request) {
        long now = Instant.now().getEpochSecond();
        McpServer s = new McpServer();
        s.setAssistantId(assistantId);
        s.setName(required(request.getName(), "name"));
        s.setDescription(request.getDescription());
        s.setTransport(validTransport(request.getTransport()));
        s.setUrl(required(request.getUrl(), "url"));
        s.setSseEndpoint(blankToNull(request.getSseEndpoint()));
        s.setAuthType(request.getAuthType() == null || request.getAuthType().isBlank() ? "none" : request.getAuthType().trim());
        s.setAuthConfig(blankToNull(request.getAuthConfig()));
        s.setEncryptedSecret(encryptSecret(request.getSecret()));
        s.setEnabled(request.getEnabled() == null || request.getEnabled());
        String id = serverRepository.create(s, now);
        log.info("Created MCP server {} ({}) for assistant {}", id, s.getName(), assistantId);
        configAuditService.recordEvent(ResourceType.mcp_server, id, assistantId, s.getName(), AuditAction.create,
                ConfigAuditService.summarize(AuditAction.create, "MCP server", s.getName()));
        return get(id);
    }

    @Override
    public McpServerDto update(String id, UpdateMcpServerRequest request) {
        McpServer existing = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        if (request.getName() != null && !request.getName().isBlank()) {
            existing.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getTransport() != null && !request.getTransport().isBlank()) {
            existing.setTransport(validTransport(request.getTransport()));
        }
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            existing.setUrl(request.getUrl().trim());
        }
        if (request.getSseEndpoint() != null) {
            existing.setSseEndpoint(blankToNull(request.getSseEndpoint()));
        }
        if (request.getAuthType() != null && !request.getAuthType().isBlank()) {
            existing.setAuthType(request.getAuthType().trim());
        }
        if (request.getAuthConfig() != null) {
            existing.setAuthConfig(blankToNull(request.getAuthConfig()));
        }
        // null secret => keep existing; non-null => replace (blank clears).
        if (request.getSecret() != null) {
            existing.setEncryptedSecret(encryptSecret(request.getSecret()));
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        serverRepository.update(existing, now);
        AuditAction action = request.getEnabled() != null
                ? ConfigAuditService.toggleAction(request.getEnabled()) : AuditAction.update;
        configAuditService.recordEvent(ResourceType.mcp_server, id, existing.getAssistantId(), existing.getName(),
                action, ConfigAuditService.summarize(action, "MCP server", existing.getName()));
        return get(id);
    }

    @Override
    public void delete(String id) {
        McpServer existing = requireEntity(id);
        serverRepository.delete(id);
        log.info("Deleted MCP server {}", id);
        configAuditService.recordEvent(ResourceType.mcp_server, id, existing.getAssistantId(), existing.getName(),
                AuditAction.delete, ConfigAuditService.summarize(AuditAction.delete, "MCP server", existing.getName()));
    }

    @Override
    public McpServerDto discover(String id) {
        McpServer server = requireEntity(id);
        long now = Instant.now().getEpochSecond();
        try {
            List<McpSchema.Tool> tools = clientFactory.discover(server);
            for (McpSchema.Tool tool : tools) {
                serverToolRepository.upsert(id, tool.name(), tool.description(), schemaJson(tool), now);
            }
            serverRepository.updateStatus(id, "connected",
                    tools.size() + " tool(s) discovered", now);
            log.info("Discovered {} tool(s) from MCP server {}", tools.size(), server.getName());
            configAuditService.recordEvent(ResourceType.mcp_server, id, server.getAssistantId(), server.getName(),
                    AuditAction.discover, tools.size() + " tool(s) discovered for MCP server " + server.getName());
        } catch (Exception e) {
            log.warn("MCP discovery failed for server {}: {}", server.getName(), e.getMessage());
            serverRepository.updateStatus(id, "error", e.getMessage(), now);
        }
        return get(id);
    }

    @Override
    public List<McpServerToolDto> listTools(String id) {
        requireEntity(id);
        return serverToolRepository.findByServer(id).stream().map(this::toToolDto).toList();
    }

    @Override
    public McpServerToolDto setToolEnabled(String toolId, boolean enabled) {
        McpServerTool tool = serverToolRepository.findById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("MCP tool not found: " + toolId));
        serverToolRepository.setEnabled(toolId, enabled, Instant.now().getEpochSecond());
        tool.setEnabled(enabled);
        AuditAction action = enabled ? AuditAction.tool_enable : AuditAction.tool_disable;
        String assistantId = serverRepository.findById(tool.getServerId()).map(McpServer::getAssistantId).orElse(null);
        configAuditService.recordEvent(ResourceType.mcp_tool, toolId, assistantId, tool.getName(), action,
                ConfigAuditService.summarize(action, "MCP tool", tool.getName()));
        return toToolDto(tool);
    }

    private McpServer requireEntity(String id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MCP server not found: " + id));
    }

    private String encryptSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return encryptionService.encrypt(secret);
    }

    private String schemaJson(McpSchema.Tool tool) {
        try {
            return tool.inputSchema() == null ? null : objectMapper.writeValueAsString(tool.inputSchema());
        } catch (Exception e) {
            return null;
        }
    }

    private String validTransport(String transport) {
        String t = transport == null || transport.isBlank() ? "streamable_http" : transport.trim();
        if (!VALID_TRANSPORTS.contains(t)) {
            throw new IllegalArgumentException("Unsupported transport: " + t);
        }
        return t;
    }

    private McpServerDto toDto(McpServer s, List<McpServerTool> tools) {
        return McpServerDto.builder()
                .id(s.getId())
                .assistantId(s.getAssistantId())
                .name(s.getName())
                .description(s.getDescription())
                .transport(s.getTransport())
                .url(s.getUrl())
                .sseEndpoint(s.getSseEndpoint())
                .authType(s.getAuthType())
                .authConfig(s.getAuthConfig())
                .hasSecret(s.getEncryptedSecret() != null && !s.getEncryptedSecret().isBlank())
                .enabled(s.isEnabled())
                .status(s.getStatus())
                .statusDetail(s.getStatusDetail())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .tools(tools == null ? null : tools.stream().map(this::toToolDto).toList())
                .build();
    }

    private McpServerToolDto toToolDto(McpServerTool t) {
        return McpServerToolDto.builder()
                .id(t.getId())
                .serverId(t.getServerId())
                .name(t.getName())
                .description(t.getDescription())
                .inputSchema(t.getInputSchema())
                .enabled(t.isEnabled())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MCP server " + field + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
