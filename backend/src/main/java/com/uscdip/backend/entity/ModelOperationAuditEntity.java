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
@Table(name = "model_operation_audit")
public class ModelOperationAuditEntity {

    @Id
    @Column(name = "operation_audit_id", length = 64)
    private String operationAuditId;

    @Column(name = "model_code", nullable = false, length = 64)
    private String modelCode;

    @Column(name = "version_no", length = 32)
    private String versionNo;

    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    @Column(name = "operator_user_id", length = 64)
    private String operatorUserId;

    @Column(name = "before_state", length = 2000)
    private String beforeState;

    @Column(name = "after_state", length = 2000)
    private String afterState;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
