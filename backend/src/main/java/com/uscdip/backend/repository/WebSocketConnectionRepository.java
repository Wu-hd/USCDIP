package com.uscdip.backend.repository;

import com.uscdip.backend.entity.WebSocketConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WebSocketConnectionRepository extends JpaRepository<WebSocketConnectionEntity, String> {

    long countByStatus(String status);

    long countByUserIdAndStatus(String userId, String status);

    Optional<WebSocketConnectionEntity> findByStompSessionIdAndStatus(String stompSessionId, String status);

    List<WebSocketConnectionEntity> findByStatusAndLastSeenAtBefore(String status, LocalDateTime cutoff);
}
