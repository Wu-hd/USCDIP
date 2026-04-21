package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record EmergencyAccountRevokeRequest(
        @NotBlank String reason
) {
}
