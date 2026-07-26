package com.ksp.agent.tool.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.tool.dto.request.CreateToolGroupRequest;
import com.ksp.agent.tool.dto.request.UpdateToolGroupRequest;
import com.ksp.agent.tool.dto.response.ToolGroupDto;
import com.ksp.agent.tool.service.ToolGroupService;
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
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.TOOL_GROUPS_PATH)
@CrossOrigin(origins = "*")
public class ToolGroupController {

    private final ToolGroupService groupService;

    public ToolGroupController(ToolGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<List<ToolGroupDto>> list(@RequestParam String assistantId) {
        return ResponseEntity.ok(groupService.list(assistantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolGroupDto> get(@PathVariable String id) {
        return ResponseEntity.ok(groupService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ToolGroupDto> create(@RequestParam String assistantId,
                                               @RequestBody CreateToolGroupRequest request) {
        return ResponseEntity.ok(groupService.create(assistantId, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ToolGroupDto> update(@PathVariable String id,
                                               @RequestBody UpdateToolGroupRequest request) {
        return ResponseEntity.ok(groupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> backfill(
            @RequestParam(required = false) String assistantId) {
        int groupsCreated = groupService.backfillUngroupedImports(assistantId);
        return ResponseEntity.ok(Map.of("groupsCreated", groupsCreated));
    }
}
