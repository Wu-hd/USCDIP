package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkOrderCloseRequest(@NotBlank String closeReason) {
}
