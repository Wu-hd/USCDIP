package com.uscdip.backend.repository;

import com.uscdip.backend.entity.FeatureViewGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FeatureViewGrantRepository extends JpaRepository<FeatureViewGrantEntity, String> {

    List<FeatureViewGrantEntity> findByUserIdAndViewLevelAndStatusAndExpiresAtAfter(
            String userId,
            String viewLevel,
            String status,
            LocalDateTime now
    );
}
