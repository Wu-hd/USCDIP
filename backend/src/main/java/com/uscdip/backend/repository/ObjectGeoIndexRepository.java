package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ObjectGeoIndexEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ObjectGeoIndexRepository extends JpaRepository<ObjectGeoIndexEntity, String> {

    Optional<ObjectGeoIndexEntity> findByObjectTypeAndObjectId(String objectType, String objectId);

    List<ObjectGeoIndexEntity> findByObjectTypeIn(Collection<String> objectTypes);
}
