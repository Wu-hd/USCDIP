package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record WorkOrderCreateRequest(
        @NotBlank String incidentId,
        String workOrderType,
        String priority,
        String description,
        String assigneeUserId,
        String assignee,
        LocalDateTime slaDueAt
) {
}
