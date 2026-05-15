package com.uscdip.backend.dto;

public record TsdbWriteSummary(
        String writeStatus,
        String writeLogId,
        int requestedCount,
        int successCount,
        int failedCount,
        Long durationMs
) {
}
