package com.uscdip.backend.repository;

import com.uscdip.backend.entity.MonitoringStationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringStationRepository extends JpaRepository<MonitoringStationEntity, String> {
}
