package com.uscdip.backend.repository;

import com.uscdip.backend.entity.CalibrationDriftRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalibrationDriftRecordRepository extends JpaRepository<CalibrationDriftRecordEntity, String> {

    List<CalibrationDriftRecordEntity> findByDeviceIdOrderByCheckedAtDescCreatedAtDesc(String deviceId);
}
