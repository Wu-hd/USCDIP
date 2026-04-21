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
@Table(name = "auth_refresh_token")
public class AuthRefreshTokenEntity {

    @Id
    @Column(name = "token_id", length = 64)
    private String tokenId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "auth_mode", nullable = false, length = 32)
    private String authMode;

    @Column(name = "emergency_account_id", length = 64)
    private String emergencyAccountId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "rotated_from_token_id", length = 64)
    private String rotatedFromTokenId;

    @Column(name = "replaced_by_token_id", length = 64)
    private String replacedByTokenId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
