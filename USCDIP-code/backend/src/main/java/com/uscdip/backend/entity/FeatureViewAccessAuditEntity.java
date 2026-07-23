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
@Table(name = "feature_view_access_audit")
public class FeatureViewAccessAuditEntity {

    @Id
    @Column(name = "audit_id", length = 64)
    private String auditId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "query_type", nullable = false, length = 64)
    private String queryType;

    @Column(name = "requested_view_level", nullable = false, length = 32)
    private String requestedViewLevel;

    @Column(name = "effective_view_level", nullable = false, length = 32)
    private String effectiveViewLevel;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision;

    @Column(name = "grant_id", length = 64)
    private String grantId;

    @Column(name = "result_count")
    private Integer resultCount;

    @Column(name = "query_conditions", length = 2000)
    private String queryConditions;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
