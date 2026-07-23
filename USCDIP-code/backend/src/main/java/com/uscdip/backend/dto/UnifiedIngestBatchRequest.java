package com.uscdip.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UnifiedIngestBatchRequest(
        @NotBlank(message = "protocolType is required")
        String protocolType,
        @NotBlank(message = "sourceType is required")
        String sourceType,
        @NotBlank(message = "sourceKey is required")
        String sourceKey,
        @NotBlank(message = "traceId is required")
        String traceId,
        @NotNull(message = "isBackfill is required")
        Boolean isBackfill,
        @NotEmpty(message = "metrics must not be empty")
        List<@Valid UnifiedIngestMetricDto> metrics
) {
}
