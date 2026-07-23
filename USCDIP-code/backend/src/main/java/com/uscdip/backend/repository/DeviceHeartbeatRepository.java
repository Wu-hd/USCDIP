package com.uscdip.backend.repository;

import com.uscdip.backend.entity.DeviceHeartbeatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceHeartbeatRepository extends JpaRepository<DeviceHeartbeatEntity, String> {

    List<DeviceHeartbeatEntity> findByDeviceIdOrderByHeartbeatTimeDesc(String deviceId);
}
