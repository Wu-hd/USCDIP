package com.uscdip.backend.repository;

import com.uscdip.backend.entity.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, String> {

    List<AlertRuleEntity> findAllByOrderByRuleCodeAsc();

    List<AlertRuleEntity> findByEnabledTrueOrderByRuleCodeAsc();

    List<AlertRuleEntity> findByRuleCodeIn(Collection<String> ruleCodes);
}
