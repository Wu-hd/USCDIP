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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "object_geo_index")
public class ObjectGeoIndexEntity {

    @Id
    @Column(name = "geo_id", length = 64)
    private String geoId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 64)
    private String objectId;

    @Column(name = "authority_srid", nullable = false, length = 32)
    private String authoritySrid;

    @Column(name = "display_srid", nullable = false, length = 32)
    private String displaySrid;

    @Lob
    @Column(name = "geometry_2d", nullable = false)
    private String geometry2d;

    @Column(name = "anchor_x", precision = 18, scale = 6)
    private BigDecimal anchorX;

    @Column(name = "anchor_y", precision = 18, scale = 6)
    private BigDecimal anchorY;

    @Column(name = "bbox_min_x", precision = 18, scale = 6)
    private BigDecimal bboxMinX;

    @Column(name = "bbox_min_y", precision = 18, scale = 6)
    private BigDecimal bboxMinY;

    @Column(name = "bbox_max_x", precision = 18, scale = 6)
    private BigDecimal bboxMaxX;

    @Column(name = "bbox_max_y", precision = 18, scale = 6)
    private BigDecimal bboxMaxY;

    @Column(name = "region_id", length = 64)
    private String regionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
