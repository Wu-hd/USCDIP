package com.uscdip.backend.repository;

import com.uscdip.backend.entity.AlertPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertPolicyRepository extends JpaRepository<AlertPolicyEntity, String> {

    List<AlertPolicyEntity> findAllByOrderByRuleCodeAsc();

    List<AlertPolicyEntity> findByEnabledTrueOrderByRuleCodeAsc();

    List<AlertPolicyEntity> findByRuleCodeIn(Collection<String> ruleCodes);

    Optional<AlertPolicyEntity> findByRuleCode(String ruleCode);
}
