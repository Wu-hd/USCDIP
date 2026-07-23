package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "master_change_request")
public class MasterChangeRequestEntity {

    @Id
    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 64)
    private String objectId;

    @Column(name = "request_status", nullable = false, length = 32)
    private String requestStatus;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Lob
    @Column(name = "request_payload_json", nullable = false)
    private String requestPayloadJson;

    @Column(name = "base_version_no", nullable = false)
    private Long baseVersionNo;

    @Column(name = "effective_version_no")
    private Long effectiveVersionNo;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
