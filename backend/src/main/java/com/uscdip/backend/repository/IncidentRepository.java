package com.uscdip.backend.repository;

import com.uscdip.backend.entity.IncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<IncidentEntity, String> {

    List<IncidentEntity> findBySegmentId(String segmentId);

    List<IncidentEntity> findByNodeId(String nodeId);
}
