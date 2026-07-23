package com.uscdip.backend.repository;

import com.uscdip.backend.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {

    List<UserRoleEntity> findByUserId(String userId);

    void deleteByUserId(String userId);
}
