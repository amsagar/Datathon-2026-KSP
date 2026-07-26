package com.ksp.agent.alert.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Alert {
    private Long id;
    private String alertType;
    private Integer districtId;
    private String districtName;
    private String crimeHead;
    private String message;
    private String severity;
    private String status;
    private String assignedTo;
    private String dedupKey;
    private Long createdAt;
    private Long updatedAt;
    private Long acknowledgedAt;
    private Long resolvedAt;
}
