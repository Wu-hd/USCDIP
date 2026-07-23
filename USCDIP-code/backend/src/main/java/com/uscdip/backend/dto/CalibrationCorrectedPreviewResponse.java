package com.uscdip.backend.dto;

import java.math.BigDecimal;

public record CalibrationCorrectedPreviewResponse(
        String sourceRecordId,
        String deviceId,
        String metricCode,
        BigDecimal rawValue,
        String profileVersion,
        BigDecimal correctionSlope,
        BigDecimal correctionOffset,
        BigDecimal correctedValue,
        Double dqScore,
        boolean calibrationExpired
) {
}
