package com.uscdip.backend.repository;

import com.uscdip.backend.entity.FeatureViewAccessAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureViewAccessAuditRepository extends JpaRepository<FeatureViewAccessAuditEntity, String> {

    List<FeatureViewAccessAuditEntity> findAllByOrderByCreatedAtDesc();
}
