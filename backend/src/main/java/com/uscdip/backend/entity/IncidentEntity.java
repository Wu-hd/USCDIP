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
        name = "incident",
        indexes = {
                @Index(name = "idx_incident_device_updated", columnList = "device_id,updated_at"),
                @Index(name = "idx_incident_type_status", columnList = "incident_type,status"),
                @Index(name = "idx_incident_source_case", columnList = "source_case_id")
        }
)
public class IncidentEntity {

    @Id
    @Column(name = "incident_id", length = 64)
    private String incidentId;

    @Column(name = "segment_id", nullable = false, length = 64)
    private String segmentId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "incident_type", nullable = false, length = 64)
    private String incidentType;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "severity_source", length = 32)
    private String severitySource;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "last_action", length = 64)
    private String lastAction;

    @Column(name = "source_case_id", length = 64)
    private String sourceCaseId;

    @Column(name = "source_alert_id", length = 64)
    private String sourceAlertId;

    @Column(name = "source_rule_code", length = 64)
    private String sourceRuleCode;

    @Column(name = "source_batch_id", length = 64)
    private String sourceBatchId;

    @Column(name = "dq_score_snapshot")
    private Double dqScoreSnapshot;

    @Column(name = "alert_conf_final")
    private Double alertConfFinal;

    @Column(name = "confirmed_by", length = 64)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "close_reason", length = 128)
    private String closeReason;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "version_no")
    private Long versionNo;
}
