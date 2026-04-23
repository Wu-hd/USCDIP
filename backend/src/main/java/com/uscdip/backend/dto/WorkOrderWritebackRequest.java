package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkOrderWritebackRequest(
        @NotBlank String writebackType,
        @NotBlank String writebackReason
) {
}
