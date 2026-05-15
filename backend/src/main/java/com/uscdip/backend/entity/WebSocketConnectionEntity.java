package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "websocket_connection",
        indexes = {
                @Index(name = "idx_ws_conn_user_status", columnList = "user_id,status"),
                @Index(name = "idx_ws_conn_session", columnList = "stomp_session_id"),
                @Index(name = "idx_ws_conn_last_seen", columnList = "last_seen_at")
        }
)
public class WebSocketConnectionEntity {

    @Id
    @Column(name = "connection_id", length = 64)
    private String connectionId;

    @Column(name = "stomp_session_id", length = 128)
    private String stompSessionId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "close_reason", length = 255)
    private String closeReason;
}
