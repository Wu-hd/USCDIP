package com.uscdip.backend.repository;

import com.uscdip.backend.entity.IdempotentRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IdempotentRecordRepository extends JpaRepository<IdempotentRecordEntity, String> {

    List<IdempotentRecordEntity> findByActionTypeAndAggregateTypeAndAggregateIdOrderByFirstSeenAtAsc(
            String actionType,
            String aggregateType,
            String aggregateId
    );
}
