package com.uscdip.backend.repository;

import com.uscdip.backend.entity.NotificationDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, String> {

    List<NotificationDeliveryEntity> findByNotificationIdOrderByChannelAsc(String notificationId);

    List<NotificationDeliveryEntity> findByNotificationIdIn(Collection<String> notificationIds);

    List<NotificationDeliveryEntity> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(String status, LocalDateTime nextRetryAt);
}
