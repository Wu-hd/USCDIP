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
@Table(name = "device_heartbeat")
public class DeviceHeartbeatEntity {

    @Id
    @Column(name = "heartbeat_id", length = 64)
    private String heartbeatId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "heartbeat_time", nullable = false)
    private LocalDateTime heartbeatTime;

    @Column(name = "recv_time", nullable = false)
    private LocalDateTime recvTime;

    @Column(name = "buffer_level")
    private Integer bufferLevel;

    @Column(name = "abnormal_flags", length = 512)
    private String abnormalFlags;

    @Column(name = "online_status_snapshot", nullable = false, length = 32)
    private String onlineStatusSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
