package com.uscdip.backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record FeatureViewGrantRequest(
        @NotBlank String userId,
        String viewLevel,
        @NotBlank String targetType,
        String targetId,
        @NotBlank String reason,
        @NotNull @Future LocalDateTime expiresAt
) {
}
