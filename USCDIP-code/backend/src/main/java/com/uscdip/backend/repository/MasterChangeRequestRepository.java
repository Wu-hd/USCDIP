package com.uscdip.backend.repository;

import com.uscdip.backend.entity.MasterChangeRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MasterChangeRequestRepository extends JpaRepository<MasterChangeRequestEntity, String> {

    List<MasterChangeRequestEntity> findByObjectTypeAndObjectIdAndRequestStatusIn(String objectType, String objectId, Collection<String> requestStatuses);
}
