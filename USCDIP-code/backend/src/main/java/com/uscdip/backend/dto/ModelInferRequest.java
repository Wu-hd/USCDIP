package com.uscdip.backend.dto;

import jakarta.validation.constraints.Min;

import java.util.Map;

public record ModelInferRequest(
        String requestId,
        String segmentId,
        String nodeId,
        Map<String, Object> features,
        @Min(0) Long simulateLatencyMs,
        Boolean simulateFailure
) {
}
