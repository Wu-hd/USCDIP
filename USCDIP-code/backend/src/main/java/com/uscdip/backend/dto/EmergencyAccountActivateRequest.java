package com.uscdip.backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EmergencyAccountActivateRequest(
        @NotBlank String password,
        @NotNull @Future LocalDateTime expiresAt,
        @NotBlank String reason
) {
}
