package com.uscdip.backend.repository;

import com.uscdip.backend.entity.AlarmEntity;
import com.uscdip.backend.model.AlarmStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmRepository extends JpaRepository<AlarmEntity, String> {

    List<AlarmEntity> findBySegmentId(String segmentId);

    List<AlarmEntity> findByNodeId(String nodeId);

    List<AlarmEntity> findByStatus(AlarmStatus status);
}
