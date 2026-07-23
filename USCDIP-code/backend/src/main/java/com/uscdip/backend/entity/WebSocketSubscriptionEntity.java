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
        name = "websocket_subscription",
        indexes = {
                @Index(name = "idx_ws_sub_conn_status", columnList = "connection_id,status"),
                @Index(name = "idx_ws_sub_user_topic", columnList = "user_id,topic")
        }
)
public class WebSocketSubscriptionEntity {

    @Id
    @Column(name = "subscription_row_id", length = 64)
    private String subscriptionRowId;

    @Column(name = "connection_id", nullable = false, length = 64)
    private String connectionId;

    @Column(name = "stomp_session_id", length = 128)
    private String stompSessionId;

    @Column(name = "subscription_id", length = 128)
    private String subscriptionId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "topic", nullable = false, length = 256)
    private String topic;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "subscribed_at", nullable = false)
    private LocalDateTime subscribedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
