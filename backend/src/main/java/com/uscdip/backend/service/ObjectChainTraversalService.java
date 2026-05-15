package com.uscdip.backend.service;

import com.uscdip.backend.entity.ObjectRelationEntity;
import com.uscdip.backend.repository.ObjectRelationRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ObjectChainTraversalService {

    public static final String RELATION_SEGMENT_START_NODE = "SEGMENT_START_NODE";
    public static final String RELATION_SEGMENT_END_NODE = "SEGMENT_END_NODE";
    public static final String RELATION_SEGMENT_FACILITY = "SEGMENT_FACILITY";
    public static final String RELATION_FACILITY_DEVICE = "FACILITY_DEVICE";
    public static final String RELATION_NODE_FACILITY = "NODE_FACILITY";

    private final ObjectRelationRepository objectRelationRepository;

    public ObjectChainTraversalService(ObjectRelationRepository objectRelationRepository) {
        this.objectRelationRepository = objectRelationRepository;
    }

    public List<String> findNodeIdsForSegment(String segmentId) {
        return objectRelationRepository.findByParentObjectTypeAndParentObjectIdAndActiveTrue(
                        ObjectScopeService.OBJECT_SEGMENT,
                        segmentId
                ).stream()
                .filter(relation -> ObjectScopeService.OBJECT_NODE.equalsIgnoreCase(relation.getChildObjectType()))
                .sorted(Comparator.comparingInt(this::relationPriority).thenComparing(ObjectRelationEntity::getChildObjectId))
                .map(ObjectRelationEntity::getChildObjectId)
                .distinct()
                .toList();
    }

    public List<String> findFacilityIdsForSegment(String segmentId) {
        return findChildIds(ObjectScopeService.OBJECT_SEGMENT, List.of(segmentId), ObjectScopeService.OBJECT_FACILITY);
    }

    public List<String> findSegmentIdsForNode(String nodeId) {
        return objectRelationRepository.findByChildObjectTypeAndChildObjectIdAndActiveTrue(
                        ObjectScopeService.OBJECT_NODE,
                        nodeId
                ).stream()
                .filter(relation -> ObjectScopeService.OBJECT_SEGMENT.equalsIgnoreCase(relation.getParentObjectType()))
                .sorted(Comparator.comparingInt(this::relationPriority).thenComparing(ObjectRelationEntity::getParentObjectId))
                .map(ObjectRelationEntity::getParentObjectId)
                .distinct()
                .toList();
    }

    public List<String> findFacilityIdsForNode(String nodeId) {
        return findChildIds(ObjectScopeService.OBJECT_NODE, List.of(nodeId), ObjectScopeService.OBJECT_FACILITY);
    }

    public List<String> findDeviceIdsForFacility(String facilityId) {
        return findChildIds(ObjectScopeService.OBJECT_FACILITY, List.of(facilityId), ObjectScopeService.OBJECT_DEVICE);
    }

    public List<String> findDeviceIdsForFacilityIds(Collection<String> facilityIds) {
        return findChildIds(ObjectScopeService.OBJECT_FACILITY, facilityIds, ObjectScopeService.OBJECT_DEVICE);
    }

    private List<String> findChildIds(String parentObjectType, Collection<String> parentObjectIds, String childObjectType) {
        if (parentObjectIds == null || parentObjectIds.isEmpty()) {
            return List.of();
        }
        Set<String> distinctIds = objectRelationRepository.findByParentObjectTypeAndParentObjectIdInAndActiveTrue(parentObjectType, parentObjectIds).stream()
                .filter(relation -> childObjectType.equalsIgnoreCase(relation.getChildObjectType()))
                .sorted(Comparator.comparingInt(this::relationPriority).thenComparing(ObjectRelationEntity::getChildObjectId))
                .map(ObjectRelationEntity::getChildObjectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(distinctIds);
    }

    private int relationPriority(ObjectRelationEntity relation) {
        String relationType = normalize(relation.getRelationType());
        if (RELATION_SEGMENT_START_NODE.equals(relationType)) {
            return 0;
        }
        if (RELATION_SEGMENT_END_NODE.equals(relationType)) {
            return 1;
        }
        return 10;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
