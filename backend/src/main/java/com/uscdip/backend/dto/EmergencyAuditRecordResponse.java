package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record EmergencyAuditRecordResponse(
        String eventType,
        String username,
        String linkedUserId,
        String sessionId,
        String outcome,
        String detail,
        LocalDateTime createdAt,
        String traceId
) {
}
