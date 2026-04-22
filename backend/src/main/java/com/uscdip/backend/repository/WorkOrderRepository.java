package com.uscdip.backend.repository;

import com.uscdip.backend.entity.WorkOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, String> {

    List<WorkOrderEntity> findBySegmentId(String segmentId);

    List<WorkOrderEntity> findByNodeId(String nodeId);

    List<WorkOrderEntity> findByIncidentIdIn(List<String> incidentIds);

    List<WorkOrderEntity> findByIncidentId(String incidentId);
}
