package com.uscdip.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UnifiedIngestMetricDto(
        @NotBlank(message = "deviceId is required")
        String deviceId,
        String protocolType,
        @NotBlank(message = "metricCode is required")
        String metricCode,
        @NotNull(message = "value is required")
        JsonNode value,
        @NotNull(message = "eventTime is required")
        LocalDateTime eventTime,
        @NotNull(message = "recvTime is required")
        LocalDateTime recvTime,
        @NotNull(message = "deviceTime is required")
        LocalDateTime deviceTime,
        String traceId,
        Boolean isBackfill,
        JsonNode attributes
) {
}
