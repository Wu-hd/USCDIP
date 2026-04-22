package com.uscdip.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CalibrationDriftRecordResponse(
        String driftRecordId,
        String profileId,
        String deviceId,
        String profileVersion,
        String metricCode,
        BigDecimal observedValue,
        BigDecimal referenceValue,
        BigDecimal deviationAbs,
        BigDecimal deviationPct,
        LocalDateTime checkedAt,
        String checkedBy,
        String driftStatus,
        String incidentId,
        String workOrderId,
        LocalDateTime createdAt
) {
}
