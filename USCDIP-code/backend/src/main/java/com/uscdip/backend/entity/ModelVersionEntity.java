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
@Table(name = "model_version")
public class ModelVersionEntity {

    @Id
    @Column(name = "version_id", length = 96)
    private String versionId;

    @Column(name = "model_code", nullable = false, length = 64)
    private String modelCode;

    @Column(name = "version_no", nullable = false, length = 32)
    private String versionNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "gray_percent")
    private Integer grayPercent;

    @Column(name = "artifact_uri", length = 512)
    private String artifactUri;

    @Column(name = "feature_schema_version", length = 64)
    private String featureSchemaVersion;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "rule_fallback_enabled", nullable = false)
    private boolean ruleFallbackEnabled;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "rolled_back_at")
    private LocalDateTime rolledBackAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
