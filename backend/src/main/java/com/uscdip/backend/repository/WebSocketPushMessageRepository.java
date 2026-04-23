package com.uscdip.backend.repository;

import com.uscdip.backend.entity.WebSocketPushMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebSocketPushMessageRepository extends JpaRepository<WebSocketPushMessageEntity, Long> {

    Optional<WebSocketPushMessageEntity> findBySourceNotificationIdAndTopic(String sourceNotificationId, String topic);

    List<WebSocketPushMessageEntity> findByTopicAndSeqNoGreaterThanOrderBySeqNoAsc(String topic, Long seqNo, Pageable pageable);
}
