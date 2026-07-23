package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ObjectRelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ObjectRelationRepository extends JpaRepository<ObjectRelationEntity, String> {

    List<ObjectRelationEntity> findByParentObjectTypeAndParentObjectIdAndActiveTrue(String parentObjectType, String parentObjectId);

    List<ObjectRelationEntity> findByParentObjectTypeAndParentObjectIdInAndActiveTrue(String parentObjectType, Collection<String> parentObjectIds);

    List<ObjectRelationEntity> findByChildObjectTypeAndChildObjectIdAndActiveTrue(String childObjectType, String childObjectId);

    List<ObjectRelationEntity> findByChildObjectTypeAndChildObjectIdInAndActiveTrue(String childObjectType, Collection<String> childObjectIds);

    List<ObjectRelationEntity> findByParentObjectTypeAndParentObjectId(String parentObjectType, String parentObjectId);

    List<ObjectRelationEntity> findByChildObjectTypeAndChildObjectId(String childObjectType, String childObjectId);

    void deleteByParentObjectTypeAndParentObjectIdAndRelationTypeIn(String parentObjectType, String parentObjectId, Collection<String> relationTypes);

    void deleteByChildObjectTypeAndChildObjectIdAndRelationTypeIn(String childObjectType, String childObjectId, Collection<String> relationTypes);
}
