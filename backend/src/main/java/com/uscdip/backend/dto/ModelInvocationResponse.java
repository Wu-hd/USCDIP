package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record ModelInvocationResponse(
        String requestId,
        String modelCode,
        String versionNo,
        String segmentId,
        String nodeId,
        String resultSource,
        String status,
        String modelResultId,
        Long latencyMs,
        String failureReason,
        String outputSummary,
        LocalDateTime createdAt
) {
}
