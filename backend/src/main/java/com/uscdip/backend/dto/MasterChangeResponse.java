package com.uscdip.backend.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record MasterChangeResponse(
        String requestId,
        String objectType,
        String objectId,
        String requestStatus,
        String requestedBy,
        String approvedBy,
        Long baseVersionNo,
        Long effectiveVersionNo,
        String reason,
        Map<String, Object> payload,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        LocalDateTime updatedAt
) {
}
