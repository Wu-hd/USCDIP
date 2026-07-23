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
@Table(name = "object_version")
public class ObjectVersionEntity {

    @Id
    @Column(name = "version_id", length = 64)
    private String versionId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 64)
    private String objectId;

    @Column(name = "version_no", nullable = false)
    private Long versionNo;

    @Column(name = "change_request_id", length = 64)
    private String changeRequestId;

    @Lob
    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;
}
