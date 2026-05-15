package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "websocket_ack",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ws_ack_user_topic", columnNames = {"user_id", "topic"})
        },
        indexes = {
                @Index(name = "idx_ws_ack_user", columnList = "user_id")
        }
)
public class WebSocketAckEntity {

    @Id
    @Column(name = "ack_id", length = 320)
    private String ackId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "topic", nullable = false, length = 256)
    private String topic;

    @Column(name = "last_ack_seq", nullable = false)
    private Long lastAckSeq;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
