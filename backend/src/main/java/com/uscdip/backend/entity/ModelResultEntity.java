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
@Table(name = "model_result")
public class ModelResultEntity {

    @Id
    @Column(name = "model_result_id", length = 64)
    private String modelResultId;

    @Column(name = "segment_id", length = 64)
    private String segmentId;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "model_code", nullable = false, length = 64)
    private String modelCode;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
