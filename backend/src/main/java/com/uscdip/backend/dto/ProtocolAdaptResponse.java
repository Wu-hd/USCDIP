package com.uscdip.backend.dto;

import java.util.List;

public record ProtocolAdaptResponse(
        String protocolType,
        int adaptedCount,
        List<UnifiedIngestMetricDto> adaptedMetrics,
        UnifiedIngestBatchResponse batch
) {
}
