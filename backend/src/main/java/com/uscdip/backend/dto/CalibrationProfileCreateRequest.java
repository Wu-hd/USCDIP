package com.uscdip.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CalibrationProfileCreateRequest(
        @NotBlank String profileVersion,
        @NotBlank String metricCode,
        @NotNull LocalDateTime calibratedAt,
        @NotNull LocalDateTime effectiveFrom,
        @NotNull LocalDateTime effectiveUntil,
        @NotBlank String operatorName,
        String referenceStandard,
        @NotNull BigDecimal correctionSlope,
        @NotNull BigDecimal correctionOffset,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal driftThresholdAbs,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal driftThresholdPct,
        boolean activate
) {
}
