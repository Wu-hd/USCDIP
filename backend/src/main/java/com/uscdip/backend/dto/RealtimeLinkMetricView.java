package com.uscdip.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RealtimeLinkMetricView {

    private String metricId;
    private String traceId;
    private String deviceId;
    private String segmentId;
    private String nodeId;
    private String chainStage;
    private LocalDateTime eventTime;
    private LocalDateTime recvTime;
    private Boolean isBackfill;
    private Long lastAckSeq;
    private String metricCode;
    private Double metricValue;
    private Long latencyMs;
}
