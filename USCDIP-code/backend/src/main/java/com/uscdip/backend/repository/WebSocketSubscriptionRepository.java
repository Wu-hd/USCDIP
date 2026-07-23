package com.uscdip.backend.repository;

import com.uscdip.backend.entity.WebSocketSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebSocketSubscriptionRepository extends JpaRepository<WebSocketSubscriptionEntity, String> {

    List<WebSocketSubscriptionEntity> findByConnectionIdAndStatus(String connectionId, String status);

    List<WebSocketSubscriptionEntity> findByStompSessionIdAndStatus(String stompSessionId, String status);
}
