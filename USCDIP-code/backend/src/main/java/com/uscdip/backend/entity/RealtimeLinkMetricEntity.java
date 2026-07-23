package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "realtime_link_metric",
        indexes = {
                @Index(name = "idx_rt_metric_trace_event", columnList = "trace_id,event_time"),
                @Index(name = "idx_rt_metric_device_event", columnList = "device_id,event_time")
        }
)
public class RealtimeLinkMetricEntity {

    @Id
    @Column(name = "metric_id", length = 64)
    private String metricId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "segment_id", nullable = false, length = 64)
    private String segmentId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "chain_stage", nullable = false, length = 64)
    private String chainStage;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "recv_time", nullable = false)
    private LocalDateTime recvTime;

    @Column(name = "is_backfill", nullable = false)
    private Boolean isBackfill;

    @Column(name = "last_ack_seq")
    private Long lastAckSeq;

    @Column(name = "metric_code", length = 64)
    private String metricCode;

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
