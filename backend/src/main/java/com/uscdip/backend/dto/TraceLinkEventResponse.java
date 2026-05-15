package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record TraceLinkEventResponse(
        String eventId,
        String traceId,
        String spanId,
        String parentSpanId,
        String stage,
        String eventType,
        String sourceModule,
        String objectType,
        String objectId,
        String status,
        Long latencyMs,
        LocalDateTime eventTime,
        LocalDateTime recvTime,
        String actorUserId,
        String clientIp,
        String routePath,
        String messageId,
        String sessionId,
        String topic,
        String detail
) {
}
