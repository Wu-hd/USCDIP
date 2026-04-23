package com.uscdip.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ModelVersionCreateRequest(
        @NotBlank String versionNo,
        String artifactUri,
        String featureSchemaVersion,
        @Min(1) Integer timeoutMs,
        Boolean ruleFallbackEnabled
) {
}
