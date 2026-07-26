package com.ksp.agent.style.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.style.dto.request.CreateStyleRequest;
import com.ksp.agent.style.dto.request.UpdateStyleRequest;
import com.ksp.agent.style.dto.response.ResponseStyleDto;
import com.ksp.agent.style.service.ResponseStyleService;
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
@RequestMapping(ApiConstants.RESPONSE_STYLES_PATH)
@CrossOrigin(origins = "*")
public class ResponseStyleController {

    private final ResponseStyleService service;

    public ResponseStyleController(ResponseStyleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ResponseStyleDto>> list(@RequestParam String assistantId) {
        return ResponseEntity.ok(service.list(assistantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseStyleDto> get(@PathVariable String id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseStyleDto> create(@RequestParam String assistantId,
                                                   @RequestBody CreateStyleRequest request) {
        return ResponseEntity.ok(service.create(assistantId, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseStyleDto> update(@PathVariable String id,
                                                   @RequestBody UpdateStyleRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseStyleDto> setDefault(@PathVariable String id) {
        return ResponseEntity.ok(service.setDefault(id));
    }
}
