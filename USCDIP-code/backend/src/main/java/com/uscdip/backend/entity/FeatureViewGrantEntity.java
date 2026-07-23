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
@Table(name = "feature_view_grant")
public class FeatureViewGrantEntity {

    @Id
    @Column(name = "grant_id", length = 64)
    private String grantId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "view_level", nullable = false, length = 32)
    private String viewLevel;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "granted_by", nullable = false, length = 64)
    private String grantedBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
