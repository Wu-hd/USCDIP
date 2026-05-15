package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureModelResultViewResponse(
        String featureResultId,
        String modelResultId,
        String segmentId,
        String nodeId,
        String regionId,
        String modelCode,
        String modelVersion,
        String status,
        String resultSource,
        String failureReason,
        LocalDateTime createdAt,
        Map<String, Object> features
) {
}
