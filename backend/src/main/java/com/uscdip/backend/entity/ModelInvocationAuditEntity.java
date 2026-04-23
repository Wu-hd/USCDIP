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
@Table(name = "model_invocation_audit")
public class ModelInvocationAuditEntity {

    @Id
    @Column(name = "invocation_audit_id", length = 64)
    private String invocationAuditId;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "model_code", nullable = false, length = 64)
    private String modelCode;

    @Column(name = "version_no", length = 32)
    private String versionNo;

    @Column(name = "segment_id", length = 64)
    private String segmentId;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "result_source", nullable = false, length = 32)
    private String resultSource;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "model_result_id", length = 64)
    private String modelResultId;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "input_summary", length = 2000)
    private String inputSummary;

    @Column(name = "output_summary", length = 2000)
    private String outputSummary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
