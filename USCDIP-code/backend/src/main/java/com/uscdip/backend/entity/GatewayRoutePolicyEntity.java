package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "gateway_route_policy")
public class GatewayRoutePolicyEntity {

    @Id
    @Column(name = "policy_id", length = 64)
    private String policyId;

    @Column(name = "route_code", nullable = false, length = 64)
    private String routeCode;

    @Column(name = "path_pattern", nullable = false, length = 256)
    private String pathPattern;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "auth_required", nullable = false)
    private boolean authRequired;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "validation_profile", nullable = false, length = 32)
    private String validationProfile;

    @Column(name = "rate_limit_scope", nullable = false, length = 32)
    private String rateLimitScope;

    @Column(name = "rate_limit_capacity")
    private Integer rateLimitCapacity;

    @Column(name = "rate_limit_window_seconds")
    private Integer rateLimitWindowSeconds;

    @Column(name = "audit_enabled", nullable = false)
    private boolean auditEnabled;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
