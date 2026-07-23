package com.uscdip.backend.repository;

import com.uscdip.backend.entity.MasterDataAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterDataAuditRepository extends JpaRepository<MasterDataAuditEntity, Long> {
}
