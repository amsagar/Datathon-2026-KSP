package com.ksp.agent.assistant.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.assistant.dto.request.CreateAssistantRequest;
import com.ksp.agent.assistant.dto.request.UpdateAssistantRequest;
import com.ksp.agent.assistant.dto.response.AssistantDto;
import com.ksp.agent.assistant.dto.response.BuiltinToolDto;
import com.ksp.agent.assistant.service.AssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiConstants.ASSISTANTS_PATH)
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping
    public ResponseEntity<List<AssistantDto>> list() {
        return ResponseEntity.ok(assistantService.list());
    }

    @GetMapping("/builtin-tools")
    public ResponseEntity<List<BuiltinToolDto>> builtinTools() {
        return ResponseEntity.ok(assistantService.builtinCatalog());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssistantDto> get(@PathVariable String id) {
        return ResponseEntity.ok(assistantService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssistantDto> create(@RequestBody CreateAssistantRequest request) {
        return ResponseEntity.ok(assistantService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssistantDto> update(@PathVariable String id,
                                               @RequestBody UpdateAssistantRequest request) {
        return ResponseEntity.ok(assistantService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        assistantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
