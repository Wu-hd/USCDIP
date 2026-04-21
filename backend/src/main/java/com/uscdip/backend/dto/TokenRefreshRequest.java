package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @NotBlank(message = "refreshToken is required") String refreshToken
) {
}
