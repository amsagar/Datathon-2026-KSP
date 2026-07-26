package com.ksp.agent.alert.service;

import com.ksp.agent.alert.entity.Alert;

import java.util.List;

public interface AlertService {

    List<Alert> list(String status, int limit);

    Alert acknowledge(long id);

    Alert resolve(long id);

    Alert assign(long id, String assignedTo);
}
