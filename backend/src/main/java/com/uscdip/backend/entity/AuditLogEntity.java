package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @Column(name = "audit_id", length = 64)
    private String auditId;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "event_category", nullable = false, length = 64)
    private String eventCategory;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "source_module", nullable = false, length = 64)
    private String sourceModule;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "actor_username", length = 128)
    private String actorUsername;

    @Column(name = "auth_mode", length = 32)
    private String authMode;

    @Column(name = "emergency_account_id", length = 64)
    private String emergencyAccountId;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "risk_level", length = 32)
    private String riskLevel;

    @Column(name = "object_type", length = 64)
    private String objectType;

    @Column(name = "object_id", length = 128)
    private String objectId;

    @Column(name = "action", length = 128)
    private String action;

    @Column(name = "http_method", length = 16)
    private String httpMethod;

    @Column(name = "request_path", length = 512)
    private String requestPath;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "detail", length = 2000)
    private String detail;

    @Lob
    @Column(name = "before_snapshot")
    private String beforeSnapshot;

    @Lob
    @Column(name = "after_snapshot")
    private String afterSnapshot;

    @Column(name = "source_table", length = 64)
    private String sourceTable;

    @Column(name = "source_record_id", length = 128)
    private String sourceRecordId;
}
