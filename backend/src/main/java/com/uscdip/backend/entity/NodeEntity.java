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
@Table(name = "node")
public class NodeEntity {

    @Id
    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "node_name", nullable = false, length = 128)
    private String nodeName;

    @Column(name = "node_type", nullable = false, length = 64)
    private String nodeType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "authority_srid", length = 32)
    private String authoritySrid;

    @Column(name = "display_srid", length = 32)
    private String displaySrid;

    @Column(name = "geometry_2d", length = 255)
    private String geometry2d;

    @Column(name = "z_top", precision = 10, scale = 2)
    private BigDecimal zTop;

    @Column(name = "z_bottom", precision = 10, scale = 2)
    private BigDecimal zBottom;

    @Column(name = "bury_depth", precision = 10, scale = 2)
    private BigDecimal buryDepth;

    @Column(name = "elevation_ref", length = 64)
    private String elevationRef;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version_no")
    private Long versionNo;
}
