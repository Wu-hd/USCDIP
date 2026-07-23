package com.uscdip.backend.dto;

import java.time.Instant;
import java.util.Map;

public record TokenPairResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String tokenType,
        Map<String, Object> userSnapshot
) {
}
