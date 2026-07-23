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
@Table(name = "ingest_batch")
public class IngestBatchEntity {

    @Id
    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "protocol_type", nullable = false, length = 32)
    private String protocolType;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_key", nullable = false, length = 128)
    private String sourceKey;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "record_count", nullable = false)
    private Integer recordCount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "is_backfill", nullable = false)
    private boolean backfill;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "tsdb_write_status", nullable = false, length = 32)
    private String tsdbWriteStatus;

    @Column(name = "last_write_log_id", length = 64)
    private String lastWriteLogId;

    @Column(name = "last_write_at")
    private LocalDateTime lastWriteAt;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "seq_no")
    private Long seqNo;

    @Column(name = "original_sample_time")
    private LocalDateTime originalSampleTime;
}
