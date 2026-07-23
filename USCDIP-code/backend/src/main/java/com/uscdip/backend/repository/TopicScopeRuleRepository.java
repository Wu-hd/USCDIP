package com.uscdip.backend.repository;

import com.uscdip.backend.entity.TopicScopeRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TopicScopeRuleRepository extends JpaRepository<TopicScopeRuleEntity, Long> {

    List<TopicScopeRuleEntity> findByRoleCodeIn(Collection<String> roleCodes);
}
