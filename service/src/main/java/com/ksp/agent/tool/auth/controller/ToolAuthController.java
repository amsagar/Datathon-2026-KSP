package com.ksp.agent.tool.auth.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.tool.auth.dto.request.CreateAuthProfileRequest;
import com.ksp.agent.tool.auth.dto.request.UpdateAuthProfileRequest;
import com.ksp.agent.tool.auth.dto.response.ToolAuthProfileDto;
import com.ksp.agent.tool.auth.service.ToolAuthProfileService;
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
@RequestMapping(ApiConstants.TOOL_AUTH_PATH)
@CrossOrigin(origins = "*")
public class ToolAuthController {

    private final ToolAuthProfileService service;

    public ToolAuthController(ToolAuthProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ToolAuthProfileDto>> list(@RequestParam String assistantId) {
        return ResponseEntity.ok(service.list(assistantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolAuthProfileDto> get(@PathVariable String id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ToolAuthProfileDto> create(@RequestParam String assistantId,
                                                     @RequestBody CreateAuthProfileRequest request) {
        return ResponseEntity.ok(service.create(assistantId, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ToolAuthProfileDto> update(@PathVariable String id,
                                                     @RequestBody UpdateAuthProfileRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
