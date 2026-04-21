package com.uscdip.backend.repository;

import com.uscdip.backend.entity.SecurityAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEntity, Long> {

    List<SecurityAuditEntity> findByEventTypeStartingWith(String eventTypePrefix, Sort sort);
}
