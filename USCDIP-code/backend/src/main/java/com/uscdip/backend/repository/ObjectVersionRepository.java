package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ObjectVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObjectVersionRepository extends JpaRepository<ObjectVersionEntity, String> {

    List<ObjectVersionEntity> findByObjectTypeAndObjectIdOrderByVersionNoDesc(String objectType, String objectId);
}
