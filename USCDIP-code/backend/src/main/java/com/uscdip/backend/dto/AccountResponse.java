package com.uscdip.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AccountResponse(
        String userId,
        String username,
        String displayName,
        String primaryRegionId,
        String status,
        List<String> roleCodes,
        Map<String, List<String>> dataScopes,
        boolean localLoginEnabled,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
