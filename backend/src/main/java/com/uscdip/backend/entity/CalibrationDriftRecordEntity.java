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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "calibration_drift_record",
        indexes = {
                @Index(name = "idx_cal_drift_device_metric_checked", columnList = "device_id,metric_code,checked_at"),
                @Index(name = "idx_cal_drift_profile", columnList = "profile_id")
        }
)
public class CalibrationDriftRecordEntity {

    @Id
    @Column(name = "drift_record_id", length = 64)
    private String driftRecordId;

    @Column(name = "profile_id", nullable = false, length = 64)
    private String profileId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "metric_code", nullable = false, length = 64)
    private String metricCode;

    @Column(name = "observed_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal observedValue;

    @Column(name = "reference_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal referenceValue;

    @Column(name = "deviation_abs", nullable = false, precision = 18, scale = 6)
    private BigDecimal deviationAbs;

    @Column(name = "deviation_pct", nullable = false, precision = 18, scale = 6)
    private BigDecimal deviationPct;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "checked_by", nullable = false, length = 128)
    private String checkedBy;

    @Column(name = "drift_status", nullable = false, length = 32)
    private String driftStatus;

    @Column(name = "work_order_id", length = 64)
    private String workOrderId;

    @Column(name = "incident_id", length = 64)
    private String incidentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
