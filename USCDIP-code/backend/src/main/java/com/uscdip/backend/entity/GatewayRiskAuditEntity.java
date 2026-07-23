package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "gateway_risk_audit")
public class GatewayRiskAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "route_code", nullable = false, length = 64)
    private String routeCode;

    @Column(name = "path_pattern", nullable = false, length = 256)
    private String pathPattern;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "auth_mode", length = 32)
    private String authMode;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "risk_level", length = 32)
    private String riskLevel;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
