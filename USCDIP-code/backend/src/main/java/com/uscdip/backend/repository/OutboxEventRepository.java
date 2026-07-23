package com.uscdip.backend.repository;

import com.uscdip.backend.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    List<OutboxEventEntity> findByStatusInAndNextRetryTimeLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses,
            LocalDateTime nextRetryTime,
            Pageable pageable
    );

    List<OutboxEventEntity> findByStatusAndClaimedAtBeforeOrderByCreatedAtAsc(String status, LocalDateTime claimedAt);

    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(String aggregateType, String aggregateId);

    List<OutboxEventEntity> findAllByOrderByCreatedAtAsc();
}
