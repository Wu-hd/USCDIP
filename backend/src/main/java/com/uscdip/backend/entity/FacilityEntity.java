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
@Table(name = "facility")
public class FacilityEntity {

    @Id
    @Column(name = "facility_id", length = 64)
    private String facilityId;

    @Column(name = "facility_name", nullable = false, length = 128)
    private String facilityName;

    @Column(name = "segment_id", nullable = false, length = 64)
    private String segmentId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "facility_type", length = 64)
    private String facilityType;

    @Column(name = "position_meter", precision = 10, scale = 2)
    private BigDecimal positionMeter;

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
