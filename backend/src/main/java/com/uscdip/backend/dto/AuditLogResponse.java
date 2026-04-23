package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        String auditId,
        LocalDateTime eventTime,
        String eventCategory,
        String eventType,
        String sourceModule,
        String actorUserId,
        String actorUsername,
        String authMode,
        String emergencyAccountId,
        String outcome,
        String riskLevel,
        String objectType,
        String objectId,
        String action,
        String httpMethod,
        String requestPath,
        String clientIp,
        String userAgent,
        String traceId,
        String detail,
        String beforeSnapshot,
        String afterSnapshot,
        String sourceTable,
        String sourceRecordId
) {
}
