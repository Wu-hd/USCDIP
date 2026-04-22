package com.uscdip.backend.repository;

import com.uscdip.backend.entity.TsWriteLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TsWriteLogRepository extends JpaRepository<TsWriteLogEntity, String> {

    List<TsWriteLogEntity> findAllByOrderByCreatedAtDesc();

    List<TsWriteLogEntity> findBySourceBatchIdIn(Collection<String> sourceBatchIds);

    Optional<TsWriteLogEntity> findTopBySourceBatchIdOrderByAttemptNoDesc(String sourceBatchId);

    List<TsWriteLogEntity> findByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(List<String> statuses, LocalDateTime nextRetryAt);
}
