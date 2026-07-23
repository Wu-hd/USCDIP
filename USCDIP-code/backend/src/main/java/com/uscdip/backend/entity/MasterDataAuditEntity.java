package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "master_data_audit")
public class MasterDataAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 64)
    private String objectId;

    @Column(name = "change_request_id", length = 64)
    private String changeRequestId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "operator_user_id", nullable = false, length = 64)
    private String operatorUserId;

    @Lob
    @Column(name = "before_snapshot_json")
    private String beforeSnapshotJson;

    @Lob
    @Column(name = "after_snapshot_json")
    private String afterSnapshotJson;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
