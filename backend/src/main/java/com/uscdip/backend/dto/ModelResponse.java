package com.uscdip.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ModelResponse(
        String modelCode,
        String modelName,
        String modelType,
        String status,
        Integer defaultTimeoutMs,
        boolean ruleFallbackEnabled,
        String description,
        String activeVersionNo,
        String grayVersionNo,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ModelVersionResponse> versions
) {
}
