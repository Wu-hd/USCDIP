package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record ModelVersionResponse(
        String versionId,
        String modelCode,
        String versionNo,
        String status,
        Integer grayPercent,
        String artifactUri,
        String featureSchemaVersion,
        Integer timeoutMs,
        boolean ruleFallbackEnabled,
        LocalDateTime publishedAt,
        LocalDateTime rolledBackAt,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
