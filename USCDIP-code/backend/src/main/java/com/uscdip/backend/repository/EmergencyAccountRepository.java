package com.uscdip.backend.repository;

import com.uscdip.backend.entity.EmergencyAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface EmergencyAccountRepository extends JpaRepository<EmergencyAccountEntity, String> {

    Optional<EmergencyAccountEntity> findByUsernameIgnoreCase(String username);

    List<EmergencyAccountEntity> findByLinkedUserIdOrderByCreatedAtAsc(String linkedUserId);
}
