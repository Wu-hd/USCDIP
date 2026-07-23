package com.uscdip.backend.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record StationPipelineLayoutResponse(
        String stationId,
        String layoutSrid,
        Map<String, Object> layout,
        LocalDateTime updatedAt
) {
}
