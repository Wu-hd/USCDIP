package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record AlertCaseResponse(
        String caseId,
        String dedupeKey,
        String deviceId,
        String segmentId,
        String nodeId,
        String ruleCode,
        String severityCurrent,
        String decisionSnapshot,
        String caseStatus,
        String firstAlertId,
        String lastAlertId,
        LocalDateTime firstTriggeredAt,
        LocalDateTime lastTriggeredAt,
        Integer hitCount,
        Integer unsuppressedHitCount,
        Integer escalationLevel,
        LocalDateTime suppressedUntil,
        LocalDateTime recoveredAt,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
