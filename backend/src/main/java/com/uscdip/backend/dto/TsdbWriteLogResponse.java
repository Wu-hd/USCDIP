package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record TsdbWriteLogResponse(
        String writeLogId,
        String sourceBatchId,
        String writeType,
        int attemptNo,
        String status,
        int requestedCount,
        int successCount,
        int failedCount,
        Long durationMs,
        String traceId,
        String lastError,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
