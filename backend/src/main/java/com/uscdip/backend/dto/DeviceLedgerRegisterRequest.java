package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record DeviceLedgerRegisterRequest(
        @NotBlank String deviceId,
        @NotBlank String deviceName,
        @NotBlank String facilityId,
        @NotBlank String segmentId,
        @NotBlank String nodeId,
        @NotBlank String protocolType,
        LocalDateTime calibrationDueAt
) {
}
