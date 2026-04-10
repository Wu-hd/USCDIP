package com.uscdip.backend.repository;

import com.uscdip.backend.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<DeviceEntity, String> {

    List<DeviceEntity> findBySegmentId(String segmentId);

    List<DeviceEntity> findByNodeId(String nodeId);

    List<DeviceEntity> findByFacilityIdIn(List<String> facilityIds);
}
