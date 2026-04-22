package com.uscdip.backend.repository;

import com.uscdip.backend.entity.TsMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TsMetricRepository extends JpaRepository<TsMetricEntity, String> {

    List<TsMetricEntity> findBySourceBatchIdOrderByEventTimeAsc(String sourceBatchId);

    List<TsMetricEntity> findAllByOrderByDqScoredAtDescEventTimeDesc();

    Optional<TsMetricEntity> findByDeviceIdAndMetricCodeAndBatchNoAndSeqNo(String deviceId, String metricCode, String batchNo, Long seqNo);

    Optional<TsMetricEntity> findTopByDeviceIdAndMetricCodeAndEventTimeBeforeOrderByEventTimeDesc(String deviceId, String metricCode, LocalDateTime eventTime);

    List<TsMetricEntity> findTop4ByDeviceIdAndMetricCodeAndEventTimeBeforeOrderByEventTimeDesc(String deviceId, String metricCode, LocalDateTime eventTime);

    long countByDeviceIdAndMetricCodeAndBatchNoAndSeqNo(String deviceId, String metricCode, String batchNo, Long seqNo);
}
