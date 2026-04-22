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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "calibration_profile",
        indexes = {
                @Index(name = "idx_cal_profile_device_metric_status", columnList = "device_id,metric_code,status"),
                @Index(name = "idx_cal_profile_effective_until", columnList = "effective_until")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cal_profile_device_metric_version", columnNames = {"device_id", "metric_code", "profile_version"})
        }
)
public class CalibrationProfileEntity {

    @Id
    @Column(name = "profile_id", length = 64)
    private String profileId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "profile_version", nullable = false, length = 64)
    private String profileVersion;

    @Column(name = "metric_code", nullable = false, length = 64)
    private String metricCode;

    @Column(name = "calibrated_at", nullable = false)
    private LocalDateTime calibratedAt;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until", nullable = false)
    private LocalDateTime effectiveUntil;

    @Column(name = "operator_name", nullable = false, length = 128)
    private String operatorName;

    @Column(name = "reference_standard", length = 255)
    private String referenceStandard;

    @Column(name = "correction_slope", nullable = false, precision = 18, scale = 6)
    private BigDecimal correctionSlope;

    @Column(name = "correction_offset", nullable = false, precision = 18, scale = 6)
    private BigDecimal correctionOffset;

    @Column(name = "drift_threshold_abs", nullable = false, precision = 18, scale = 6)
    private BigDecimal driftThresholdAbs;

    @Column(name = "drift_threshold_pct", nullable = false, precision = 18, scale = 6)
    private BigDecimal driftThresholdPct;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
