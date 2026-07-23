package com.uscdip.backend.repository;

import com.uscdip.backend.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<PermissionEntity, String> {

    List<PermissionEntity> findByPermissionCodeIn(Collection<String> permissionCodes);
}
