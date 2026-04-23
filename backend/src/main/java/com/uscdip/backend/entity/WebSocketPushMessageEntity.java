package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
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
        name = "websocket_push_message",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ws_push_notification_topic", columnNames = {"source_notification_id", "topic"})
        },
        indexes = {
                @Index(name = "idx_ws_push_topic_seq", columnList = "topic,seq_no"),
                @Index(name = "idx_ws_push_recipient", columnList = "recipient_user_id,seq_no"),
                @Index(name = "idx_ws_push_trace", columnList = "trace_id")
        }
)
public class WebSocketPushMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq_no")
    private Long seqNo;

    @Column(name = "message_id", nullable = false, unique = true, length = 64)
    private String messageId;

    @Column(name = "topic", nullable = false, length = 256)
    private String topic;

    @Column(name = "recipient_user_id", length = 64)
    private String recipientUserId;

    @Column(name = "source_notification_id", nullable = false, length = 64)
    private String sourceNotificationId;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "message_type", nullable = false, length = 64)
    private String messageType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
