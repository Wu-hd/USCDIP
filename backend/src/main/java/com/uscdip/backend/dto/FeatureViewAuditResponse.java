package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record FeatureViewAuditResponse(
        String auditId,
        String userId,
        String username,
        String queryType,
        String requestedViewLevel,
        String effectiveViewLevel,
        String decision,
        String grantId,
        Integer resultCount,
        String queryConditions,
        String reason,
        LocalDateTime createdAt
) {
}
