package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record AlertRecordResponse(
        String alertId,
        String sourceRecordId,
        String sourceBatchId,
        String deviceId,
        String segmentId,
        String nodeId,
        String ruleCode,
        String severity,
        String decision,
        Double alertConfRaw,
        Double alertConfFinal,
        Double dqScoreSnapshot,
        String dqLevelSnapshot,
        Double dqAlarmConfFactor,
        String metricCode,
        String metricValue,
        LocalDateTime eventTime,
        String traceId,
        String caseId,
        String dedupeKey,
        String processStatus,
        Boolean suppressed,
        Integer escalationLevel,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {
}
