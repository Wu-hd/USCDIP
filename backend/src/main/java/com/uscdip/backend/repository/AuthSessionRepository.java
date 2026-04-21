package com.uscdip.backend.repository;

import com.uscdip.backend.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {

    List<AuthSessionEntity> findByUserId(String userId);
}
