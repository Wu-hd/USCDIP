package com.uscdip.backend.repository;

import com.uscdip.backend.entity.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {

    List<RolePermissionEntity> findByRoleCodeIn(Collection<String> roleCodes);
}
