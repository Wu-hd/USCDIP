package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ModelResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelResultRepository extends JpaRepository<ModelResultEntity, String> {

    List<ModelResultEntity> findBySegmentId(String segmentId);

    List<ModelResultEntity> findByNodeId(String nodeId);
}
