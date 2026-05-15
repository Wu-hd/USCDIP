package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(
        name = "notification_message",
        indexes = {
                @Index(name = "idx_notification_source_event", columnList = "source_event_id"),
                @Index(name = "idx_notification_aggregate", columnList = "aggregate_type,aggregate_id"),
                @Index(name = "idx_notification_recipient_status", columnList = "recipient_user_id,recipient_username,status"),
                @Index(name = "idx_notification_trace", columnList = "trace_id"),
                @Index(name = "idx_notification_created", columnList = "created_at")
        }
)
public class NotificationMessageEntity {

    @Id
    @Column(name = "notification_id", length = 64)
    private String notificationId;

    @Column(name = "source_event_id", nullable = false, unique = true, length = 64)
    private String sourceEventId;

    @Column(name = "idempotent_key", nullable = false, length = 256)
    private String idempotentKey;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "recipient_user_id", length = 64)
    private String recipientUserId;

    @Column(name = "recipient_username", length = 64)
    private String recipientUsername;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;
}
