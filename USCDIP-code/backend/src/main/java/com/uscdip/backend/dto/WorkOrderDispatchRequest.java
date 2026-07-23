package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record WorkOrderDispatchRequest(
        String assigneeUserId,
        @NotBlank String assignee,
        LocalDateTime slaDueAt,
        String reason
) {
}
