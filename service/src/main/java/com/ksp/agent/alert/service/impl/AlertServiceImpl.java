package com.ksp.agent.alert.service.impl;

import com.ksp.agent.alert.entity.Alert;
import com.ksp.agent.alert.repo.AlertRepository;
import com.ksp.agent.alert.service.AlertService;
import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AlertServiceImpl implements AlertService {

    private final AlertRepository repository;

    public AlertServiceImpl(AlertRepository repository) {
        this.repository = repository;
    }

    @Override
    public java.util.List<Alert> list(String status, int limit) {
        int cap = limit <= 0 ? 100 : Math.min(limit, 500);
        return repository.findByStatus(status, cap);
    }

    @Override
    public Alert acknowledge(long id) {
        return updateStatus(id, "ACKNOWLEDGED", null);
    }

    @Override
    public Alert resolve(long id) {
        return updateStatus(id, "RESOLVED", null);
    }

    @Override
    public Alert assign(long id, String assignedTo) {
        repository.assign(id, assignedTo, Instant.now().getEpochSecond());
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));
    }

    private Alert updateStatus(long id, String status, String assignedTo) {
        repository.updateStatus(id, status, assignedTo, Instant.now().getEpochSecond());
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));
    }
}
