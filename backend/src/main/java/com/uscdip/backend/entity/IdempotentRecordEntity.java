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
        name = "idempotent_record",
        indexes = {
                @Index(name = "idx_idempotent_action_aggregate", columnList = "action_type,aggregate_type,aggregate_id"),
                @Index(name = "idx_idempotent_event", columnList = "event_id"),
                @Index(name = "idx_idempotent_status", columnList = "status")
        }
)
public class IdempotentRecordEntity {

    @Id
    @Column(name = "idempotent_key", length = 256)
    private String idempotentKey;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "version_no", nullable = false)
    private Long versionNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "result_ref_id", length = 128)
    private String resultRefId;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Lob
    @Column(name = "last_error")
    private String lastError;
}
