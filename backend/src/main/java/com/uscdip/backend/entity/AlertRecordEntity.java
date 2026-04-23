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
        name = "alert_record",
        indexes = {
                @Index(name = "idx_alert_record_device_created", columnList = "device_id,created_at"),
                @Index(name = "idx_alert_record_batch_created", columnList = "source_batch_id,created_at"),
                @Index(name = "idx_alert_record_rule_created", columnList = "rule_code,created_at")
        }
)
public class AlertRecordEntity {

    @Id
    @Column(name = "alert_id", length = 64)
    private String alertId;

    @Column(name = "source_record_id", nullable = false, length = 64)
    private String sourceRecordId;

    @Column(name = "source_batch_id", nullable = false, length = 64)
    private String sourceBatchId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "segment_id", length = 64)
    private String segmentId;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision;

    @Column(name = "alert_conf_raw", nullable = false)
    private Double alertConfRaw;

    @Column(name = "alert_conf_final", nullable = false)
    private Double alertConfFinal;

    @Column(name = "dq_score_snapshot")
    private Double dqScoreSnapshot;

    @Column(name = "dq_level_snapshot", length = 8)
    private String dqLevelSnapshot;

    @Column(name = "dq_alarm_conf_factor")
    private Double dqAlarmConfFactor;

    @Column(name = "metric_code", length = 64)
    private String metricCode;

    @Column(name = "metric_value", length = 512)
    private String metricValue;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
