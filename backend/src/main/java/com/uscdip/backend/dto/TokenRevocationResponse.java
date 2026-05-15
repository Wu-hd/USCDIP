package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record TokenRevocationResponse(
        String userId,
        String action,
        LocalDateTime tokenValidAfter,
        int revokedSessionCount,
        int revokedRefreshTokenCount
) {
}
