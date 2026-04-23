package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ModelInvocationAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelInvocationAuditRepository extends JpaRepository<ModelInvocationAuditEntity, String> {

    List<ModelInvocationAuditEntity> findByModelCodeOrderByCreatedAtDesc(String modelCode);
}
