package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkOrderCompleteRequest(@NotBlank String completionSummary) {
}
