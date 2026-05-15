package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ModelOperationAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelOperationAuditRepository extends JpaRepository<ModelOperationAuditEntity, String> {

    List<ModelOperationAuditEntity> findByModelCodeOrderByCreatedAtDesc(String modelCode);
}
