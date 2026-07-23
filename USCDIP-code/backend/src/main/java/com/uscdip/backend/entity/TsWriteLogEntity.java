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
        name = "ts_write_log",
        indexes = {
                @Index(name = "idx_ts_write_log_batch_attempt", columnList = "source_batch_id,attempt_no"),
                @Index(name = "idx_ts_write_log_status_retry", columnList = "status,next_retry_at")
        }
)
public class TsWriteLogEntity {

    @Id
    @Column(name = "write_log_id", length = 64)
    private String writeLogId;

    @Column(name = "source_batch_id", nullable = false, length = 64)
    private String sourceBatchId;

    @Column(name = "write_type", nullable = false, length = 32)
    private String writeType;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "requested_count", nullable = false)
    private Integer requestedCount;

    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
