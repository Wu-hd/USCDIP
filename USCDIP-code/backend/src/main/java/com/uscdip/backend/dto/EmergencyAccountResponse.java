package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record EmergencyAccountResponse(
        String accountId,
        String username,
        String linkedUserId,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime activatedAt,
        String activatedBy
) {
}
