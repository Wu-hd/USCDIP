package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceHeartbeatReportRequest(
        @NotNull LocalDateTime heartbeatTime,
        @NotNull LocalDateTime recvTime,
        Integer bufferLevel,
        List<String> abnormalFlags
) {
}
