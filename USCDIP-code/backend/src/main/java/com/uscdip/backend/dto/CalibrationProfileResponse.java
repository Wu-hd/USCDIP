package com.uscdip.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CalibrationProfileResponse(
        String profileId,
        String deviceId,
        String profileVersion,
        String metricCode,
        LocalDateTime calibratedAt,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        String operatorName,
        String referenceStandard,
        BigDecimal correctionSlope,
        BigDecimal correctionOffset,
        BigDecimal driftThresholdAbs,
        BigDecimal driftThresholdPct,
        String status,
        boolean calibrationExpired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
