package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureMetricViewResponse(
        String featureRecordId,
        String sourceRecordId,
        String deviceId,
        String deviceName,
        String maskedDeviceId,
        String segmentId,
        String nodeId,
        String regionId,
        String metricCode,
        String metricValue,
        LocalDateTime eventTime,
        LocalDateTime timeBucket,
        Double dqScore,
        String dqLevel,
        String dqFlags,
        String geometry2d,
        String coordinateBucket,
        Map<String, Object> features
) {
}
