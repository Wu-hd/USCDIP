package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ObjectScopeBindingRepository extends JpaRepository<ObjectScopeBindingEntity, String> {

    Optional<ObjectScopeBindingEntity> findByObjectTypeAndObjectId(String objectType, String objectId);

    List<ObjectScopeBindingEntity> findByObjectTypeAndObjectIdIn(String objectType, Collection<String> objectIds);
}
