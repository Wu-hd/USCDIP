package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record AlertPolicyResponse(
        String policyId,
        String ruleCode,
        Integer dedupeWindowSeconds,
        Integer suppressWindowSeconds,
        Integer recoveryWindowSeconds,
        Integer escalateThresholdL1,
        Integer escalateThresholdL2,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
