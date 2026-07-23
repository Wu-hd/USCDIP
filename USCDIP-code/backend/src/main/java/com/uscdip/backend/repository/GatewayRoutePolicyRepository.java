package com.uscdip.backend.repository;

import com.uscdip.backend.entity.GatewayRoutePolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GatewayRoutePolicyRepository extends JpaRepository<GatewayRoutePolicyEntity, String> {

    List<GatewayRoutePolicyEntity> findByEnabledTrue();
}
