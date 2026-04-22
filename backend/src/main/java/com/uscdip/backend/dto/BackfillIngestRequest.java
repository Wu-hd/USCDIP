package com.uscdip.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public record BackfillIngestRequest(
        @NotBlank(message = "protocolType is required")
        String protocolType,
        @NotBlank(message = "sourceType is required")
        String sourceType,
        @NotBlank(message = "sourceKey is required")
        String sourceKey,
        @NotBlank(message = "traceId is required")
        String traceId,
        @NotBlank(message = "batchNo is required")
        String batchNo,
        @NotNull(message = "seqNo is required")
        @Positive(message = "seqNo must be greater than 0")
        Long seqNo,
        @NotNull(message = "originalSampleTime is required")
        LocalDateTime originalSampleTime,
        @NotNull(message = "isBackfill is required")
        Boolean isBackfill,
        @NotEmpty(message = "metrics must not be empty")
        List<@Valid UnifiedIngestMetricDto> metrics
) {

    @AssertTrue(message = "isBackfill must be true")
    public boolean backfillEnabled() {
        return Boolean.TRUE.equals(isBackfill);
    }
}
