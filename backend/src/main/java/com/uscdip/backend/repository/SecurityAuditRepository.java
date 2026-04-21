package com.uscdip.backend.repository;

import com.uscdip.backend.entity.SecurityAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEntity, Long> {
}
