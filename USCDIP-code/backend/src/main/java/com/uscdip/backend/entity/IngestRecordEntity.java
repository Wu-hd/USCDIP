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
@Table(name = "ingest_record")
public class IngestRecordEntity {

    @Id
    @Column(name = "record_id", length = 64)
    private String recordId;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "metric_code", nullable = false, length = 64)
    private String metricCode;

    @Column(name = "metric_value", nullable = false, length = 512)
    private String metricValue;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "recv_time", nullable = false)
    private LocalDateTime recvTime;

    @Column(name = "device_time", nullable = false)
    private LocalDateTime deviceTime;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "is_backfill", nullable = false)
    private boolean backfill;

    @Column(name = "adapter_type", nullable = false, length = 32)
    private String adapterType;

    @Column(name = "attributes_json", length = 2000)
    private String attributesJson;

    @Column(name = "raw_payload_excerpt", length = 2000)
    private String rawPayloadExcerpt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
