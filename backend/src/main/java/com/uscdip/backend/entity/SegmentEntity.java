package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "segment")
public class SegmentEntity {

    @Id
    @Column(name = "segment_id", length = 64)
    private String segmentId;

    @Column(name = "segment_name", nullable = false, length = 128)
    private String segmentName;

    @Column(name = "start_node_id", nullable = false, length = 64)
    private String startNodeId;

    @Column(name = "end_node_id", nullable = false, length = 64)
    private String endNodeId;

    @Column(name = "segment_type", length = 64)
    private String segmentType;

    @Column(name = "length_meter", precision = 10, scale = 2)
    private BigDecimal lengthMeter;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version_no")
    private Long versionNo;
}
