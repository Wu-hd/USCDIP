package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record IncidentResponse(
        String incidentId,
        String incidentType,
        String segmentId,
        String nodeId,
        String deviceId,
        String title,
        String severity,
        String severitySource,
        String status,
        String sourceCaseId,
        String sourceAlertId,
        String sourceRuleCode,
        String sourceBatchId,
        Double dqScoreSnapshot,
        Double alertConfFinal,
        String confirmedBy,
        LocalDateTime confirmedAt,
        LocalDateTime resolvedAt,
        String closeReason,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long versionNo
) {
}
