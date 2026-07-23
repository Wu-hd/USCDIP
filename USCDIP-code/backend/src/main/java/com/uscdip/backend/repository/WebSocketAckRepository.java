package com.uscdip.backend.repository;

import com.uscdip.backend.entity.WebSocketAckEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebSocketAckRepository extends JpaRepository<WebSocketAckEntity, String> {

    Optional<WebSocketAckEntity> findByUserIdAndTopic(String userId, String topic);
}
