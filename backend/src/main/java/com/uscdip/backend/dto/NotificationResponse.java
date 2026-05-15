package com.uscdip.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationResponse(
        String notificationId,
        String sourceEventId,
        String idempotentKey,
        String aggregateType,
        String aggregateId,
        String eventType,
        String recipientUserId,
        String recipientUsername,
        String title,
        String content,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        String lastError,
        List<NotificationDeliveryResponse> deliveries
) {
}
