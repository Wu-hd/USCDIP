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
        name = "alert_rule",
        indexes = {
                @Index(name = "idx_alert_rule_enabled", columnList = "enabled"),
                @Index(name = "idx_alert_rule_type_metric", columnList = "rule_type,metric_code")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alert_rule_code", columnNames = {"rule_code"})
        }
)
public class AlertRuleEntity {

    @Id
    @Column(name = "rule_id", length = 64)
    private String ruleId;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(name = "metric_code", length = 64)
    private String metricCode;

    @Column(name = "operator_code", length = 32)
    private String operator;

    @Column(name = "threshold_low")
    private Double thresholdLow;

    @Column(name = "threshold_high")
    private Double thresholdHigh;

    @Column(name = "logic_type", length = 32)
    private String logicType;

    @Column(name = "base_confidence")
    private Double baseConfidence;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "expression_json", length = 2000)
    private String expressionJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
