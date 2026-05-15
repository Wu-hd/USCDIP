package com.uscdip.backend.repository;

import com.uscdip.backend.entity.TraceLinkEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TraceLinkEventRepository extends JpaRepository<TraceLinkEventEntity, String> {

    List<TraceLinkEventEntity> findAllByOrderByEventTimeDescEventIdDesc();

    List<TraceLinkEventEntity> findByTraceIdOrderByEventTimeAscEventIdAsc(String traceId);
}
