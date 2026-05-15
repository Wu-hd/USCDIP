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
        name = "alert_case",
        indexes = {
                @Index(name = "idx_alert_case_device_updated", columnList = "device_id,updated_at"),
                @Index(name = "idx_alert_case_rule_status", columnList = "rule_code,case_status"),
                @Index(name = "idx_alert_case_status_triggered", columnList = "case_status,last_triggered_at"),
                @Index(name = "idx_alert_case_dedupe", columnList = "dedupe_key")
        }
)
public class AlertCaseEntity {

    @Id
    @Column(name = "case_id", length = 64)
    private String caseId;

    @Column(name = "dedupe_key", nullable = false, length = 255)
    private String dedupeKey;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "segment_id", length = 64)
    private String segmentId;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "severity_current", nullable = false, length = 32)
    private String severityCurrent;

    @Column(name = "decision_snapshot", nullable = false, length = 32)
    private String decisionSnapshot;

    @Column(name = "case_status", nullable = false, length = 32)
    private String caseStatus;

    @Column(name = "first_alert_id", nullable = false, length = 64)
    private String firstAlertId;

    @Column(name = "last_alert_id", nullable = false, length = 64)
    private String lastAlertId;

    @Column(name = "first_triggered_at", nullable = false)
    private LocalDateTime firstTriggeredAt;

    @Column(name = "last_triggered_at", nullable = false)
    private LocalDateTime lastTriggeredAt;

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount;

    @Column(name = "unsuppressed_hit_count", nullable = false)
    private Integer unsuppressedHitCount;

    @Column(name = "escalation_level", nullable = false)
    private Integer escalationLevel;

    @Column(name = "suppressed_until")
    private LocalDateTime suppressedUntil;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
