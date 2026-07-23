package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "ts_metric",
        indexes = {
                @Index(name = "idx_ts_metric_device_metric_event", columnList = "device_id,metric_code,event_time"),
                @Index(name = "idx_ts_metric_source_batch", columnList = "source_batch_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ts_metric_backfill_dedupe", columnNames = {"device_id", "metric_code", "batch_no", "seq_no"})
        }
)
public class TsMetricEntity {

    @Id
    @Column(name = "source_record_id", length = 64)
    private String sourceRecordId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "metric_code", nullable = false, length = 64)
    private String metricCode;

    @Column(name = "metric_value", nullable = false, length = 512)
    private String metricValue;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "recv_time", nullable = false)
    private LocalDateTime recvTime;

    @Column(name = "device_time", nullable = false)
    private LocalDateTime deviceTime;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "is_backfill", nullable = false)
    private boolean backfill;

    @Column(name = "source_batch_id", nullable = false, length = 64)
    private String sourceBatchId;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "seq_no")
    private Long seqNo;

    @Column(name = "original_sample_time")
    private LocalDateTime originalSampleTime;

    @Column(name = "late_arrival", nullable = false)
    private boolean lateArrival;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "dq_score")
    private Double dqScore;

    @Column(name = "dq_level", length = 8)
    private String dqLevel;

    @Column(name = "dq_flags", length = 512)
    private String dqFlags;

    @Column(name = "dq_completeness")
    private Double dqCompleteness;

    @Column(name = "dq_validity")
    private Double dqValidity;

    @Column(name = "dq_timeliness")
    private Double dqTimeliness;

    @Column(name = "dq_consistency")
    private Double dqConsistency;

    @Column(name = "dq_stability")
    private Double dqStability;

    @Column(name = "dq_alarm_conf_factor")
    private Double dqAlarmConfFactor;

    @Column(name = "dq_scored_at")
    private LocalDateTime dqScoredAt;
}
