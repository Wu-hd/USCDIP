package com.uscdip.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceLedgerResponse(
        String deviceId,
        String deviceName,
        String facilityId,
        String segmentId,
        String nodeId,
        String regionId,
        String protocolType,
        String onlineStatus,
        String onlineStatusReason,
        LocalDateTime lastHeartbeat,
        LocalDateTime lastRecvTime,
        Integer bufferLevel,
        List<String> abnormalFlags,
        LocalDateTime calibrationDueAt,
        boolean calibrationExpired,
        Long versionNo
) {
}
