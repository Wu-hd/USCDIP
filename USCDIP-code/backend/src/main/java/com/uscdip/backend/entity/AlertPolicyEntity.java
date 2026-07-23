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
        name = "alert_policy",
        indexes = {
                @Index(name = "idx_alert_policy_enabled", columnList = "enabled"),
                @Index(name = "idx_alert_policy_rule_enabled", columnList = "rule_code,enabled")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alert_policy_rule_code", columnNames = {"rule_code"})
        }
)
public class AlertPolicyEntity {

    @Id
    @Column(name = "policy_id", length = 64)
    private String policyId;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "dedupe_window_seconds", nullable = false)
    private Integer dedupeWindowSeconds;

    @Column(name = "suppress_window_seconds", nullable = false)
    private Integer suppressWindowSeconds;

    @Column(name = "recovery_window_seconds", nullable = false)
    private Integer recoveryWindowSeconds;

    @Column(name = "escalate_threshold_l1", nullable = false)
    private Integer escalateThresholdL1;

    @Column(name = "escalate_threshold_l2", nullable = false)
    private Integer escalateThresholdL2;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
