package com.uscdip.backend.repository;

import com.uscdip.backend.entity.NotificationMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessageEntity, String> {

    Optional<NotificationMessageEntity> findBySourceEventId(String sourceEventId);

    List<NotificationMessageEntity> findAllByOrderByCreatedAtDescNotificationIdDesc();
}
