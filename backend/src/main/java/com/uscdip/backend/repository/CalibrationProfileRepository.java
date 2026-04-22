package com.uscdip.backend.repository;

import com.uscdip.backend.entity.CalibrationProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalibrationProfileRepository extends JpaRepository<CalibrationProfileEntity, String> {

    List<CalibrationProfileEntity> findByDeviceIdOrderByCalibratedAtDescCreatedAtDesc(String deviceId);

    List<CalibrationProfileEntity> findByDeviceIdAndMetricCodeOrderByCalibratedAtDescCreatedAtDesc(String deviceId, String metricCode);

    Optional<CalibrationProfileEntity> findFirstByDeviceIdAndMetricCodeAndStatusOrderByEffectiveFromDescCreatedAtDesc(
            String deviceId,
            String metricCode,
            String status
    );

    Optional<CalibrationProfileEntity> findFirstByDeviceIdAndMetricCodeAndProfileVersionOrderByCreatedAtDesc(
            String deviceId,
            String metricCode,
            String profileVersion
    );

    List<CalibrationProfileEntity> findByStatusInOrderByEffectiveUntilAscCreatedAtAsc(Collection<String> statuses);
}
