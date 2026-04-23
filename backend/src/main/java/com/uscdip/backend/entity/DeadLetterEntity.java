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
        name = "dead_letter",
        indexes = {
                @Index(name = "idx_dead_letter_source_event", columnList = "source_event_id"),
                @Index(name = "idx_dead_letter_idempotent", columnList = "idempotent_key"),
                @Index(name = "idx_dead_letter_status_created", columnList = "status,created_at")
        }
)
public class DeadLetterEntity {

    @Id
    @Column(name = "dead_letter_id", length = 64)
    private String deadLetterId;

    @Column(name = "source_event_id", length = 64)
    private String sourceEventId;

    @Column(name = "idempotent_key", nullable = false, length = 256)
    private String idempotentKey;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Lob
    @Column(name = "payload")
    private String payload;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Lob
    @Column(name = "failure_reason", nullable = false)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
