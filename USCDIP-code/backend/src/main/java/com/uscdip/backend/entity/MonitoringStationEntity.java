package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
@Table(name = "monitoring_station")
public class MonitoringStationEntity {

    @Id
    @Column(name = "station_id", length = 64)
    private String stationId;

    @Column(name = "station_name", nullable = false, length = 128)
    private String stationName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "province_adcode", nullable = false, length = 16)
    private String provinceAdcode;

    @Column(name = "city_adcode", nullable = false, length = 16)
    private String cityAdcode;

    @Column(name = "longitude", nullable = false, precision = 12, scale = 6)
    private BigDecimal longitude;

    @Column(name = "latitude", nullable = false, precision = 12, scale = 6)
    private BigDecimal latitude;

    @Column(name = "authority_srid", nullable = false, length = 32)
    private String authoritySrid;

    @Column(name = "display_srid", nullable = false, length = 32)
    private String displaySrid;

    @Lob
    @Column(name = "station_info_json", nullable = false)
    private String stationInfoJson;

    @Lob
    @Column(name = "pipeline_layout_geo_json", nullable = false)
    private String pipelineLayoutGeoJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version_no")
    private Long versionNo;
}
