package com.uscdip.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ModelRegisterRequest(
        @NotBlank String modelCode,
        @NotBlank String modelName,
        String modelType,
        @Min(1) Integer defaultTimeoutMs,
        Boolean ruleFallbackEnabled,
        String description
) {
}
