package com.uscdip.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UnifiedIngestBatchResponse(
        String batchId,
        String protocolType,
        String sourceType,
        String sourceKey,
        String traceId,
        boolean isBackfill,
        String status,
        int totalRecordCount,
        int visibleRecordCount,
        LocalDateTime receivedAt,
        List<UnifiedIngestMetricDto> records,
        TsdbWriteSummary tsdbWrite,
        DataQualityBatchSummary dqScore
) {
}
