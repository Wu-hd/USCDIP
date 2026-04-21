package com.uscdip.backend.repository;

import com.uscdip.backend.entity.GatewayClientRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GatewayClientRuleRepository extends JpaRepository<GatewayClientRuleEntity, String> {

    List<GatewayClientRuleEntity> findByEnabledTrue();
}
