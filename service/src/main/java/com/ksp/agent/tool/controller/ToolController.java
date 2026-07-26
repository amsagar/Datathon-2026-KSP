package com.ksp.agent.tool.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.tool.dto.request.CreateToolRequest;
import com.ksp.agent.tool.dto.request.TestToolRequest;
import com.ksp.agent.tool.dto.request.UpdateToolRequest;
import com.ksp.agent.tool.dto.response.AgentToolDto;
import com.ksp.agent.tool.dto.response.TestToolResult;
import com.ksp.agent.tool.imports.ToolImportService;
import com.ksp.agent.tool.imports.dto.ImportRequest;
import com.ksp.agent.tool.imports.dto.ImportResult;
import com.ksp.agent.tool.service.AgentToolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping(ApiConstants.TOOLS_PATH)
public class ToolController {

    private final AgentToolService toolService;
    private final ToolImportService importService;

    public ToolController(AgentToolService toolService, ToolImportService importService) {
        this.toolService = toolService;
        this.importService = importService;
    }

    @GetMapping
    public ResponseEntity<List<AgentToolDto>> list(@RequestParam String assistantId) {
        return ResponseEntity.ok(toolService.list(assistantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentToolDto> get(@PathVariable String id) {
        return ResponseEntity.ok(toolService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentToolDto> create(@RequestParam String assistantId,
                                               @RequestBody CreateToolRequest request) {
        return ResponseEntity.ok(toolService.create(assistantId, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentToolDto> update(@PathVariable String id,
                                               @RequestBody UpdateToolRequest request) {
        return ResponseEntity.ok(toolService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        toolService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestToolResult> test(@PathVariable String id,
                                               @RequestBody(required = false) TestToolRequest request) {
        return ResponseEntity.ok(toolService.test(id, request));
    }

    @PostMapping("/import/{kind}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportResult> importTools(@PathVariable String kind,
                                                    @RequestParam String assistantId,
                                                    @RequestBody ImportRequest request) {
        return ResponseEntity.ok(importService.importByKind(kind, assistantId, request));
    }
}
