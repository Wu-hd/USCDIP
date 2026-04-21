package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record EmergencyLoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
