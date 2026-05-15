package com.uscdip.backend.repository;

import com.uscdip.backend.entity.GatewayRiskAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GatewayRiskAuditRepository extends JpaRepository<GatewayRiskAuditEntity, Long> {

    List<GatewayRiskAuditEntity> findByTraceId(String traceId);
}
