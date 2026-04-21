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

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "device")
public class DeviceEntity {

    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Column(name = "facility_id", nullable = false, length = 64)
    private String facilityId;

    @Column(name = "segment_id", nullable = false, length = 64)
    private String segmentId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "protocol_type", length = 64)
    private String protocolType;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

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
