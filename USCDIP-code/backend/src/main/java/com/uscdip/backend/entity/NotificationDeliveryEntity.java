package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "notification_delivery",
        indexes = {
                @Index(name = "idx_delivery_notification", columnList = "notification_id"),
                @Index(name = "idx_delivery_status_retry", columnList = "status,next_retry_at"),
                @Index(name = "idx_delivery_channel_status", columnList = "channel,status")
        }
)
public class NotificationDeliveryEntity {

    @Id
    @Column(name = "delivery_id", length = 64)
    private String deliveryId;

    @Column(name = "notification_id", nullable = false, length = 64)
    private String notificationId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "target", nullable = false, length = 255)
    private String target;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;
}
