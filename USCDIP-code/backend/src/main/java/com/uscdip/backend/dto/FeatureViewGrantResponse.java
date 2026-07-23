package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record FeatureViewGrantResponse(
        String grantId,
        String userId,
        String viewLevel,
        String targetType,
        String targetId,
        String reason,
        String grantedBy,
        LocalDateTime expiresAt,
        String status,
        LocalDateTime createdAt,
        LocalDateTime revokedAt
) {
}
