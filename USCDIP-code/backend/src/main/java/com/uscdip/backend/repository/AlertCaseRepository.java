package com.uscdip.backend.repository;

import com.uscdip.backend.entity.AlertCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AlertCaseRepository extends JpaRepository<AlertCaseEntity, String> {

    List<AlertCaseEntity> findAllByOrderByUpdatedAtDescCaseIdDesc();

    List<AlertCaseEntity> findByRuleCodeAndCaseStatusInOrderByLastTriggeredAtDesc(String ruleCode, Collection<String> caseStatuses);

    List<AlertCaseEntity> findByCaseStatusInAndLastTriggeredAtBefore(Collection<String> caseStatuses, java.time.LocalDateTime cutoff);
}
