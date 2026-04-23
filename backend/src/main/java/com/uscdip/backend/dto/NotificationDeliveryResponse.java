package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record NotificationDeliveryResponse(
        String deliveryId,
        String channel,
        String target,
        String status,
        Integer attemptCount,
        LocalDateTime nextRetryAt,
        LocalDateTime sentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String lastError
) {
}
