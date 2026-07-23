package com.uscdip.backend.repository;

import com.uscdip.backend.entity.SegmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SegmentRepository extends JpaRepository<SegmentEntity, String> {

    List<SegmentEntity> findByStartNodeIdOrEndNodeId(String startNodeId, String endNodeId);
}
