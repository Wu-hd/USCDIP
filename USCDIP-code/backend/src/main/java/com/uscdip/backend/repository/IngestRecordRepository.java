package com.uscdip.backend.repository;

import com.uscdip.backend.entity.IngestRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IngestRecordRepository extends JpaRepository<IngestRecordEntity, String> {

    List<IngestRecordEntity> findByBatchIdOrderByEventTimeAsc(String batchId);

    List<IngestRecordEntity> findByBatchIdIn(Collection<String> batchIds);
}
