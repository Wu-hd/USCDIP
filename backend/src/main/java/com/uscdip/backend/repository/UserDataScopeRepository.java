package com.uscdip.backend.repository;

import com.uscdip.backend.entity.UserDataScopeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDataScopeRepository extends JpaRepository<UserDataScopeEntity, Long> {

    List<UserDataScopeEntity> findByUserId(String userId);
}
