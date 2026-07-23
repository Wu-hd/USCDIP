package com.uscdip.backend.repository;

import com.uscdip.backend.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {

    List<AuditLogEntity> findAllByOrderByEventTimeDescAuditIdDesc();
}
