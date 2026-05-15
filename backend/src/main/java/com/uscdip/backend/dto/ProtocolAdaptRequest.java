package com.uscdip.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProtocolAdaptRequest(
        @NotBlank(message = "sourceType is required")
        String sourceType,
        @NotBlank(message = "sourceKey is required")
        String sourceKey,
        @NotBlank(message = "traceId is required")
        String traceId,
        @NotNull(message = "isBackfill is required")
        Boolean isBackfill,
        @NotNull(message = "payload is required")
        JsonNode payload
) {
}
