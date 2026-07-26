package com.ksp.agent.mcp.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.mcp.dto.request.CreateMcpServerRequest;
import com.ksp.agent.mcp.dto.request.UpdateMcpServerRequest;
import com.ksp.agent.mcp.dto.request.UpdateMcpToolRequest;
import com.ksp.agent.mcp.dto.response.McpServerDto;
import com.ksp.agent.mcp.dto.response.McpServerToolDto;
import com.ksp.agent.mcp.service.McpServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiConstants.MCP_SERVERS_PATH)
@CrossOrigin(origins = "*")
public class McpServerController {

    private final McpServerService mcpServerService;

    public McpServerController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @GetMapping
    public ResponseEntity<List<McpServerDto>> list(@RequestParam String assistantId) {
        return ResponseEntity.ok(mcpServerService.list(assistantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<McpServerDto> get(@PathVariable String id) {
        return ResponseEntity.ok(mcpServerService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<McpServerDto> create(@RequestParam String assistantId,
                                               @RequestBody CreateMcpServerRequest request) {
        return ResponseEntity.ok(mcpServerService.create(assistantId, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<McpServerDto> update(@PathVariable String id,
                                               @RequestBody UpdateMcpServerRequest request) {
        return ResponseEntity.ok(mcpServerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        mcpServerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** (Re)connect to the server, discover its tools, and persist them. Returns the refreshed server. */
    @PostMapping("/{id}/discover")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<McpServerDto> discover(@PathVariable String id) {
        return ResponseEntity.ok(mcpServerService.discover(id));
    }

    @GetMapping("/{id}/tools")
    public ResponseEntity<List<McpServerToolDto>> listTools(@PathVariable String id) {
        return ResponseEntity.ok(mcpServerService.listTools(id));
    }

    @PatchMapping("/{id}/tools/{toolId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<McpServerToolDto> setToolEnabled(@PathVariable String id,
                                                           @PathVariable String toolId,
                                                           @RequestBody UpdateMcpToolRequest request) {
        boolean enabled = request.getEnabled() != null && request.getEnabled();
        return ResponseEntity.ok(mcpServerService.setToolEnabled(toolId, enabled));
    }
}
