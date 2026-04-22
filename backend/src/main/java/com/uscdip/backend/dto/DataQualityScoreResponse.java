package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record DataQualityScoreResponse(
        String sourceRecordId,
        String sourceBatchId,
        String deviceId,
        String metricCode,
        String metricValue,
        LocalDateTime eventTime,
        LocalDateTime recvTime,
        LocalDateTime deviceTime,
        boolean isBackfill,
        Double dqScore,
        String dqLevel,
        String dqFlags,
        Double dqCompleteness,
        Double dqValidity,
        Double dqTimeliness,
        Double dqConsistency,
        Double dqStability,
        Double dqAlarmConfFactor,
        LocalDateTime dqScoredAt
) {
}
