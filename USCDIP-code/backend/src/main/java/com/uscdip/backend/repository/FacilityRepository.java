package com.uscdip.backend.repository;

import com.uscdip.backend.entity.FacilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacilityRepository extends JpaRepository<FacilityEntity, String> {

    List<FacilityEntity> findBySegmentId(String segmentId);

    List<FacilityEntity> findByNodeId(String nodeId);
}
