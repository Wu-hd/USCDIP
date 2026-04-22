package com.uscdip.backend.repository;

import com.uscdip.backend.entity.IngestBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngestBatchRepository extends JpaRepository<IngestBatchEntity, String> {

    List<IngestBatchEntity> findAllByOrderByReceivedAtDesc();
}
