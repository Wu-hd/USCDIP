package com.uscdip.backend.repository;

import com.uscdip.backend.entity.AlertRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRecordRepository extends JpaRepository<AlertRecordEntity, String> {

    List<AlertRecordEntity> findAllByOrderByCreatedAtDescAlertIdDesc();
}
