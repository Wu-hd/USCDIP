package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record WebSocketPushPayload(
        Long seqNo,
        String messageId,
        String topic,
        String type,
        String sourceNotificationId,
        String traceId,
        String title,
        String content,
        LocalDateTime createdAt
) {
}
