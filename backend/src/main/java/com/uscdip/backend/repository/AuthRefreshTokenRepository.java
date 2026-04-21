package com.uscdip.backend.repository;

import com.uscdip.backend.entity.AuthRefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshTokenEntity, String> {

    Optional<AuthRefreshTokenEntity> findByTokenHash(String tokenHash);

    List<AuthRefreshTokenEntity> findBySessionId(String sessionId);

    List<AuthRefreshTokenEntity> findByUserId(String userId);

    List<AuthRefreshTokenEntity> findByEmergencyAccountId(String emergencyAccountId);
}
