package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CalibrationDriftCheckRequest(
        @NotBlank String profileVersion,
        @NotBlank String metricCode,
        @NotNull BigDecimal observedValue,
        @NotNull BigDecimal referenceValue,
        @NotNull LocalDateTime checkedAt,
        @NotBlank String checkedBy
) {
}
