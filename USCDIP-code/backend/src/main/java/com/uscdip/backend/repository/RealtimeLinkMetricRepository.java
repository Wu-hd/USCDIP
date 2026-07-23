package com.uscdip.backend.repository;

import com.uscdip.backend.entity.RealtimeLinkMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RealtimeLinkMetricRepository extends JpaRepository<RealtimeLinkMetricEntity, String> {

    List<RealtimeLinkMetricEntity> findTop50ByOrderByEventTimeDesc();

    List<RealtimeLinkMetricEntity> findByTraceIdOrderByEventTimeAsc(String traceId);
}
