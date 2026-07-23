package com.uscdip.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.MasterChangeDecisionRequest;
import com.uscdip.backend.dto.MasterChangeResponse;
import com.uscdip.backend.dto.MasterChangeSubmitRequest;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.FacilityEntity;
import com.uscdip.backend.entity.MasterChangeRequestEntity;
import com.uscdip.backend.entity.MasterDataAuditEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.MonitoringStationEntity;
import com.uscdip.backend.entity.ObjectRelationEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.ObjectVersionEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.FacilityRepository;
import com.uscdip.backend.repository.MasterChangeRequestRepository;
import com.uscdip.backend.repository.MasterDataAuditRepository;
import com.uscdip.backend.repository.NodeRepository;
import com.uscdip.backend.repository.MonitoringStationRepository;
import com.uscdip.backend.repository.ObjectRelationRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.ObjectVersionRepository;
import com.uscdip.backend.repository.SegmentRepository;
import com.uscdip.backend.support.TraceIdContext;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MasterDataChangeService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final Set<String> ACTIVE_REQUEST_STATUSES = Set.of(STATUS_PENDING, STATUS_QUEUED);
    private static final String ACTION_SUBMIT = "SUBMIT";
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_REJECT = "REJECT";
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_CONFLICT = "CONFLICT";

    private final MasterChangeRequestRepository masterChangeRequestRepository;
    private final ObjectVersionRepository objectVersionRepository;
    private final MasterDataAuditRepository masterDataAuditRepository;
    private final NodeRepository nodeRepository;
    private final SegmentRepository segmentRepository;
    private final FacilityRepository facilityRepository;
    private final DeviceRepository deviceRepository;
    private final MonitoringStationRepository monitoringStationRepository;
    private final ObjectRelationRepository objectRelationRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final ObjectScopeService objectScopeService;
    private final ObjectGeoIndexService objectGeoIndexService;
    private final ObjectMapper objectMapper;

    public MasterDataChangeService(
            MasterChangeRequestRepository masterChangeRequestRepository,
            ObjectVersionRepository objectVersionRepository,
            MasterDataAuditRepository masterDataAuditRepository,
            NodeRepository nodeRepository,
            SegmentRepository segmentRepository,
            FacilityRepository facilityRepository,
            DeviceRepository deviceRepository,
            MonitoringStationRepository monitoringStationRepository,
            ObjectRelationRepository objectRelationRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            ObjectScopeService objectScopeService,
            ObjectGeoIndexService objectGeoIndexService,
            ObjectMapper objectMapper
    ) {
        this.masterChangeRequestRepository = masterChangeRequestRepository;
        this.objectVersionRepository = objectVersionRepository;
        this.masterDataAuditRepository = masterDataAuditRepository;
        this.nodeRepository = nodeRepository;
        this.segmentRepository = segmentRepository;
        this.facilityRepository = facilityRepository;
        this.deviceRepository = deviceRepository;
        this.monitoringStationRepository = monitoringStationRepository;
        this.objectRelationRepository = objectRelationRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.objectScopeService = objectScopeService;
        this.objectGeoIndexService = objectGeoIndexService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MasterChangeResponse submitChange(AuthorizationContext context, MasterChangeSubmitRequest request) {
        String objectType = normalizeObjectType(request.objectType());
        String objectId = request.objectId().trim();
        objectScopeService.requireAccess(context, objectType, objectId, "MENU:ASSET:WRITE");

        VersionedObject currentObject = loadVersionedObject(objectType, objectId);
        if (!currentObject.versionNo().equals(request.baseVersionNo())) {
            audit(objectType, objectId, null, ACTION_SUBMIT, OUTCOME_CONFLICT, context.userId(), snapshot(currentObject.entity()), null);
            throw conflict("Base version does not match current version");
        }

        validatePayload(objectType, request.payload());
        String requestStatus = resolveInitialStatus(objectType, objectId);
        MasterChangeRequestEntity entity = new MasterChangeRequestEntity(
                "MCR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT),
                objectType,
                objectId,
                requestStatus,
                context.userId(),
                null,
                toJson(request.payload()),
                request.baseVersionNo(),
                null,
                request.reason(),
                LocalDateTime.now(),
                null,
                LocalDateTime.now()
        );
        masterChangeRequestRepository.save(entity);
        audit(objectType, objectId, entity.getRequestId(), ACTION_SUBMIT, OUTCOME_SUCCESS, context.userId(), snapshot(currentObject.entity()), null);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<MasterChangeResponse> listChanges(
            AuthorizationContext context,
            int page,
            int pageSize,
            String objectType,
            String objectId,
            String requestStatus
    ) {
        List<MasterChangeRequestEntity> changes = masterChangeRequestRepository.findAll().stream()
                .filter(change -> context.platformAdmin() || change.getRequestedBy().equalsIgnoreCase(context.userId()))
                .filter(change -> objectType == null || objectType.isBlank() || normalizeObjectType(change.getObjectType()).equals(normalizeObjectType(objectType)))
                .filter(change -> objectId == null || objectId.isBlank() || change.getObjectId().equalsIgnoreCase(objectId))
                .filter(change -> requestStatus == null || requestStatus.isBlank() || normalize(change.getRequestStatus()).equals(normalize(requestStatus)))
                .sorted(Comparator.comparing(MasterChangeRequestEntity::getCreatedAt).reversed())
                .toList();

        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= changes.size()) {
            return PageResponse.of(List.of(), changes.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, changes.size());
        return PageResponse.of(changes.subList(fromIndex, toIndex).stream().map(this::toResponse).toList(), changes.size(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Optional<MasterChangeResponse> getChange(AuthorizationContext context, String requestId) {
        return masterChangeRequestRepository.findById(requestId)
                .filter(change -> context.platformAdmin() || change.getRequestedBy().equalsIgnoreCase(context.userId()))
                .map(this::toResponse);
    }

    @Transactional
    public MasterChangeResponse approveChange(AuthorizationContext operator, String requestId, MasterChangeDecisionRequest decisionRequest) {
        MasterChangeRequestEntity change = masterChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.MASTER_CHANGE_NOT_FOUND, HttpStatus.NOT_FOUND, "Change request not found: " + requestId));

        if (!STATUS_PENDING.equalsIgnoreCase(change.getRequestStatus()) && !STATUS_QUEUED.equalsIgnoreCase(change.getRequestStatus())) {
            throw new AuthFlowException(ErrorCode.MASTER_CHANGE_INVALID_STATE, HttpStatus.CONFLICT, "Change request is not approvable: " + change.getRequestStatus());
        }

        if (STATUS_QUEUED.equalsIgnoreCase(change.getRequestStatus())
                && hasPendingSibling(change.getObjectType(), change.getObjectId(), change.getRequestId())) {
            throw new AuthFlowException(ErrorCode.MASTER_CHANGE_PENDING, HttpStatus.CONFLICT, ErrorCode.MASTER_CHANGE_PENDING.defaultMessage());
        }

        VersionedObject currentObject = loadVersionedObject(change.getObjectType(), change.getObjectId());
        if (!currentObject.versionNo().equals(change.getBaseVersionNo())) {
            audit(change.getObjectType(), change.getObjectId(), change.getRequestId(), ACTION_APPROVE, OUTCOME_CONFLICT, operator.userId(), snapshot(currentObject.entity()), null);
            throw conflict("Current version does not match change request base version");
        }

        String beforeSnapshot = snapshot(currentObject.entity());
        Map<String, Object> payload = readJsonMap(change.getRequestPayloadJson());
        try {
            VersionedObject updatedObject = applyApprovedChange(change.getObjectType(), currentObject.entity(), payload);
            String afterSnapshot = snapshot(updatedObject.entity());
            change.setRequestStatus(STATUS_APPROVED);
            change.setApprovedBy(operator.userId());
            change.setReason(decisionRequest == null || decisionRequest.reason() == null || decisionRequest.reason().isBlank() ? change.getReason() : decisionRequest.reason());
            change.setApprovedAt(LocalDateTime.now());
            change.setUpdatedAt(LocalDateTime.now());
            change.setEffectiveVersionNo(updatedObject.versionNo());
            masterChangeRequestRepository.save(change);
            objectVersionRepository.save(new ObjectVersionEntity(
                    "OV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT),
                    change.getObjectType(),
                    change.getObjectId(),
                    updatedObject.versionNo(),
                    change.getRequestId(),
                    afterSnapshot,
                    LocalDateTime.now(),
                    operator.userId()
            ));
            audit(change.getObjectType(), change.getObjectId(), change.getRequestId(), ACTION_APPROVE, OUTCOME_SUCCESS, operator.userId(), beforeSnapshot, afterSnapshot);
            return toResponse(change);
        } catch (ObjectOptimisticLockingFailureException ex) {
            audit(change.getObjectType(), change.getObjectId(), change.getRequestId(), ACTION_APPROVE, OUTCOME_CONFLICT, operator.userId(), beforeSnapshot, null);
            throw conflict("Master data was updated concurrently");
        }
    }

    @Transactional
    public MasterChangeResponse rejectChange(AuthorizationContext operator, String requestId, MasterChangeDecisionRequest decisionRequest) {
        MasterChangeRequestEntity change = masterChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.MASTER_CHANGE_NOT_FOUND, HttpStatus.NOT_FOUND, "Change request not found: " + requestId));

        if (!STATUS_PENDING.equalsIgnoreCase(change.getRequestStatus()) && !STATUS_QUEUED.equalsIgnoreCase(change.getRequestStatus())) {
            throw new AuthFlowException(ErrorCode.MASTER_CHANGE_INVALID_STATE, HttpStatus.CONFLICT, "Change request is not rejectable: " + change.getRequestStatus());
        }

        VersionedObject currentObject = loadVersionedObject(change.getObjectType(), change.getObjectId());
        String beforeSnapshot = snapshot(currentObject.entity());
        change.setRequestStatus(STATUS_REJECTED);
        change.setApprovedBy(operator.userId());
        change.setApprovedAt(LocalDateTime.now());
        change.setUpdatedAt(LocalDateTime.now());
        if (decisionRequest != null && decisionRequest.reason() != null && !decisionRequest.reason().isBlank()) {
            change.setReason(decisionRequest.reason());
        }
        masterChangeRequestRepository.save(change);
        audit(change.getObjectType(), change.getObjectId(), change.getRequestId(), ACTION_REJECT, OUTCOME_SUCCESS, operator.userId(), beforeSnapshot, null);
        return toResponse(change);
    }

    private String resolveInitialStatus(String objectType, String objectId) {
        if (ObjectScopeService.OBJECT_NODE.equals(objectType) || ObjectScopeService.OBJECT_SEGMENT.equals(objectType)) {
            boolean hasActive = masterChangeRequestRepository.findByObjectTypeAndObjectIdAndRequestStatusIn(objectType, objectId, ACTIVE_REQUEST_STATUSES).stream()
                    .anyMatch(change -> STATUS_PENDING.equalsIgnoreCase(change.getRequestStatus()));
            return hasActive ? STATUS_QUEUED : STATUS_PENDING;
        }
        return STATUS_PENDING;
    }

    private boolean hasPendingSibling(String objectType, String objectId, String requestId) {
        return masterChangeRequestRepository.findByObjectTypeAndObjectIdAndRequestStatusIn(objectType, objectId, List.of(STATUS_PENDING)).stream()
                .anyMatch(change -> !change.getRequestId().equalsIgnoreCase(requestId));
    }

    private VersionedObject loadVersionedObject(String objectType, String objectId) {
        return switch (objectType) {
            case ObjectScopeService.OBJECT_NODE -> nodeRepository.findById(objectId)
                    .map(entity -> new VersionedObject(entity, entity.getVersionNo()))
                    .orElseThrow(() -> new AuthFlowException(ErrorCode.NODE_NOT_FOUND, HttpStatus.NOT_FOUND, "Node not found: " + objectId));
            case ObjectScopeService.OBJECT_SEGMENT -> segmentRepository.findById(objectId)
                    .map(entity -> new VersionedObject(entity, entity.getVersionNo()))
                    .orElseThrow(() -> new AuthFlowException(ErrorCode.SEGMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Segment not found: " + objectId));
            case ObjectScopeService.OBJECT_FACILITY -> facilityRepository.findById(objectId)
                    .map(entity -> new VersionedObject(entity, entity.getVersionNo()))
                    .orElseThrow(() -> new AuthFlowException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Facility not found: " + objectId));
            case ObjectScopeService.OBJECT_DEVICE -> deviceRepository.findById(objectId)
                    .map(entity -> new VersionedObject(entity, entity.getVersionNo()))
                    .orElseThrow(() -> new AuthFlowException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Device not found: " + objectId));
            case ObjectScopeService.OBJECT_STATION -> monitoringStationRepository.findById(objectId)
                    .map(entity -> new VersionedObject(entity, entity.getVersionNo()))
                    .orElseThrow(() -> new AuthFlowException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Station not found: " + objectId));
            default -> throw new IllegalArgumentException("Unsupported object type: " + objectType);
        };
    }

    private VersionedObject applyApprovedChange(String objectType, Object entity, Map<String, Object> payload) {
        return switch (objectType) {
            case ObjectScopeService.OBJECT_NODE -> applyNodeChange((NodeEntity) entity, payload);
            case ObjectScopeService.OBJECT_SEGMENT -> applySegmentChange((SegmentEntity) entity, payload);
            case ObjectScopeService.OBJECT_FACILITY -> applyFacilityChange((FacilityEntity) entity, payload);
            case ObjectScopeService.OBJECT_DEVICE -> applyDeviceChange((DeviceEntity) entity, payload);
            case ObjectScopeService.OBJECT_STATION -> applyStationChange((MonitoringStationEntity) entity, payload);
            default -> throw new IllegalArgumentException("Unsupported object type: " + objectType);
        };
    }

    private VersionedObject applyNodeChange(NodeEntity entity, Map<String, Object> payload) {
        applyString(payload, "nodeName", entity::setNodeName);
        applyString(payload, "nodeType", entity::setNodeType);
        applyString(payload, "status", entity::setStatus);
        applyString(payload, "authoritySrid", entity::setAuthoritySrid);
        applyString(payload, "displaySrid", entity::setDisplaySrid);
        applyString(payload, "geometry2d", entity::setGeometry2d);
        applyBigDecimal(payload, "zTop", entity::setZTop);
        applyBigDecimal(payload, "zBottom", entity::setZBottom);
        applyBigDecimal(payload, "buryDepth", entity::setBuryDepth);
        applyString(payload, "elevationRef", entity::setElevationRef);
        entity.setUpdatedAt(LocalDateTime.now());
        NodeEntity saved = nodeRepository.saveAndFlush(entity);
        updateBindingRegion(ObjectScopeService.OBJECT_NODE, entity.getNodeId(), payload, null);
        objectGeoIndexService.refreshObjectAndDependents(ObjectScopeService.OBJECT_NODE, entity.getNodeId());
        return new VersionedObject(saved, saved.getVersionNo());
    }

    private VersionedObject applySegmentChange(SegmentEntity entity, Map<String, Object> payload) {
        applyString(payload, "segmentName", entity::setSegmentName);
        applyString(payload, "segmentType", entity::setSegmentType);
        applyString(payload, "status", entity::setStatus);
        applyBigDecimal(payload, "lengthMeter", entity::setLengthMeter);
        if (payload.containsKey("startNodeId")) {
            String startNodeId = readRequiredReference(payload, "startNodeId");
            requireNode(startNodeId);
            entity.setStartNodeId(startNodeId);
        }
        if (payload.containsKey("endNodeId")) {
            String endNodeId = readRequiredReference(payload, "endNodeId");
            requireNode(endNodeId);
            entity.setEndNodeId(endNodeId);
        }
        entity.setUpdatedAt(LocalDateTime.now());
        SegmentEntity saved = segmentRepository.saveAndFlush(entity);
        replaceSegmentNodeRelations(entity.getSegmentId(), entity.getStartNodeId(), entity.getEndNodeId());
        updateBindingRegion(ObjectScopeService.OBJECT_SEGMENT, entity.getSegmentId(), payload, null);
        objectGeoIndexService.refreshObjectAndDependents(ObjectScopeService.OBJECT_SEGMENT, entity.getSegmentId());
        return new VersionedObject(saved, saved.getVersionNo());
    }

    private VersionedObject applyFacilityChange(FacilityEntity entity, Map<String, Object> payload) {
        applyString(payload, "facilityName", entity::setFacilityName);
        applyString(payload, "facilityType", entity::setFacilityType);
        applyString(payload, "status", entity::setStatus);
        applyBigDecimal(payload, "positionMeter", entity::setPositionMeter);
        if (payload.containsKey("segmentId")) {
            String segmentId = readRequiredReference(payload, "segmentId");
            requireSegment(segmentId);
            entity.setSegmentId(segmentId);
        }
        if (payload.containsKey("nodeId")) {
            String nodeId = readRequiredReference(payload, "nodeId");
            requireNode(nodeId);
            entity.setNodeId(nodeId);
        }
        entity.setUpdatedAt(LocalDateTime.now());
        FacilityEntity saved = facilityRepository.saveAndFlush(entity);
        replaceFacilityRelations(entity.getFacilityId(), entity.getSegmentId(), entity.getNodeId());
        String inferredRegion = inferRegionFromBindings(ObjectScopeService.OBJECT_SEGMENT, entity.getSegmentId(), ObjectScopeService.OBJECT_NODE, entity.getNodeId());
        updateBindingRegion(ObjectScopeService.OBJECT_FACILITY, entity.getFacilityId(), payload, inferredRegion);
        cascadeDeviceTopologyFromFacility(entity);
        objectGeoIndexService.refreshObjectAndDependents(ObjectScopeService.OBJECT_FACILITY, entity.getFacilityId());
        return new VersionedObject(saved, saved.getVersionNo());
    }

    private VersionedObject applyDeviceChange(DeviceEntity entity, Map<String, Object> payload) {
        applyString(payload, "deviceName", entity::setDeviceName);
        applyString(payload, "protocolType", entity::setProtocolType);
        applyString(payload, "status", entity::setStatus);
        if (payload.containsKey("facilityId")) {
            String facilityId = readRequiredReference(payload, "facilityId");
            FacilityEntity facility = requireFacility(facilityId);
            entity.setFacilityId(facilityId);
            if (!payload.containsKey("segmentId")) {
                entity.setSegmentId(facility.getSegmentId());
            }
            if (!payload.containsKey("nodeId")) {
                entity.setNodeId(facility.getNodeId());
            }
        }
        if (payload.containsKey("segmentId")) {
            String segmentId = readRequiredReference(payload, "segmentId");
            requireSegment(segmentId);
            entity.setSegmentId(segmentId);
        }
        if (payload.containsKey("nodeId")) {
            String nodeId = readRequiredReference(payload, "nodeId");
            requireNode(nodeId);
            entity.setNodeId(nodeId);
        }
        if (payload.containsKey("lastHeartbeat")) {
            entity.setLastHeartbeat(LocalDateTime.parse(String.valueOf(payload.get("lastHeartbeat"))));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        DeviceEntity saved = deviceRepository.saveAndFlush(entity);
        replaceDeviceRelations(entity.getDeviceId(), entity.getFacilityId());
        String inferredRegion = objectScopeBindingRepository.findByObjectTypeAndObjectId(ObjectScopeService.OBJECT_FACILITY, entity.getFacilityId())
                .map(ObjectScopeBindingEntity::getRegionId)
                .orElse(null);
        updateBindingRegion(ObjectScopeService.OBJECT_DEVICE, entity.getDeviceId(), payload, inferredRegion);
        objectGeoIndexService.refreshObjectAndDependents(ObjectScopeService.OBJECT_DEVICE, entity.getDeviceId());
        return new VersionedObject(saved, saved.getVersionNo());
    }

    private VersionedObject applyStationChange(MonitoringStationEntity entity, Map<String, Object> payload) {
        applyString(payload, "stationName", entity::setStationName);
        applyString(payload, "status", entity::setStatus);
        applyString(payload, "provinceAdcode", entity::setProvinceAdcode);
        applyString(payload, "cityAdcode", entity::setCityAdcode);
        applyString(payload, "authoritySrid", entity::setAuthoritySrid);
        applyString(payload, "displaySrid", entity::setDisplaySrid);
        applyBigDecimal(payload, "longitude", entity::setLongitude);
        applyBigDecimal(payload, "latitude", entity::setLatitude);
        if (payload.containsKey("stationInfo")) {
            entity.setStationInfoJson(toJson(payload.get("stationInfo")));
        }
        if (payload.containsKey("pipelineLayout")) {
            entity.setPipelineLayoutGeoJson(toJson(payload.get("pipelineLayout")));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        MonitoringStationEntity saved = monitoringStationRepository.saveAndFlush(entity);
        updateBindingRegion(ObjectScopeService.OBJECT_STATION, entity.getStationId(), payload, null);
        objectGeoIndexService.refreshObject(ObjectScopeService.OBJECT_STATION, entity.getStationId());
        return new VersionedObject(saved, saved.getVersionNo());
    }

    private void cascadeDeviceTopologyFromFacility(FacilityEntity facility) {
        List<DeviceEntity> childDevices = deviceRepository.findByFacilityId(facility.getFacilityId());
        String facilityRegion = objectScopeBindingRepository.findByObjectTypeAndObjectId(ObjectScopeService.OBJECT_FACILITY, facility.getFacilityId())
                .map(ObjectScopeBindingEntity::getRegionId)
                .orElse(null);
        for (DeviceEntity device : childDevices) {
            device.setSegmentId(facility.getSegmentId());
            device.setNodeId(facility.getNodeId());
            device.setUpdatedAt(LocalDateTime.now());
            deviceRepository.saveAndFlush(device);
            replaceDeviceRelations(device.getDeviceId(), device.getFacilityId());
            updateBindingRegion(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId(), Map.of(), facilityRegion);
        }
    }

    private void replaceSegmentNodeRelations(String segmentId, String startNodeId, String endNodeId) {
        objectRelationRepository.deleteByParentObjectTypeAndParentObjectIdAndRelationTypeIn(
                ObjectScopeService.OBJECT_SEGMENT,
                segmentId,
                List.of(
                        ObjectChainTraversalService.RELATION_SEGMENT_START_NODE,
                        ObjectChainTraversalService.RELATION_SEGMENT_END_NODE
                )
        );
        objectRelationRepository.saveAll(List.of(
                relation("OR-" + segmentId + "-START-" + shortId(), ObjectScopeService.OBJECT_SEGMENT, segmentId, ObjectScopeService.OBJECT_NODE, startNodeId, ObjectChainTraversalService.RELATION_SEGMENT_START_NODE),
                relation("OR-" + segmentId + "-END-" + shortId(), ObjectScopeService.OBJECT_SEGMENT, segmentId, ObjectScopeService.OBJECT_NODE, endNodeId, ObjectChainTraversalService.RELATION_SEGMENT_END_NODE)
        ));
    }

    private void replaceFacilityRelations(String facilityId, String segmentId, String nodeId) {
        objectRelationRepository.deleteByChildObjectTypeAndChildObjectIdAndRelationTypeIn(
                ObjectScopeService.OBJECT_FACILITY,
                facilityId,
                List.of(ObjectChainTraversalService.RELATION_SEGMENT_FACILITY, ObjectChainTraversalService.RELATION_NODE_FACILITY)
        );
        objectRelationRepository.saveAll(List.of(
                relation("OR-" + segmentId + "-FAC-" + shortId(), ObjectScopeService.OBJECT_SEGMENT, segmentId, ObjectScopeService.OBJECT_FACILITY, facilityId, ObjectChainTraversalService.RELATION_SEGMENT_FACILITY),
                relation("OR-" + nodeId + "-FAC-" + shortId(), ObjectScopeService.OBJECT_NODE, nodeId, ObjectScopeService.OBJECT_FACILITY, facilityId, ObjectChainTraversalService.RELATION_NODE_FACILITY)
        ));
    }

    private void replaceDeviceRelations(String deviceId, String facilityId) {
        objectRelationRepository.deleteByChildObjectTypeAndChildObjectIdAndRelationTypeIn(
                ObjectScopeService.OBJECT_DEVICE,
                deviceId,
                List.of(ObjectChainTraversalService.RELATION_FACILITY_DEVICE)
        );
        objectRelationRepository.save(relation(
                "OR-" + facilityId + "-DEV-" + shortId(),
                ObjectScopeService.OBJECT_FACILITY,
                facilityId,
                ObjectScopeService.OBJECT_DEVICE,
                deviceId,
                ObjectChainTraversalService.RELATION_FACILITY_DEVICE
        ));
    }

    private ObjectRelationEntity relation(String relationId, String parentType, String parentId, String childType, String childId, String relationType) {
        return new ObjectRelationEntity(
                relationId,
                parentType,
                parentId,
                childType,
                childId,
                relationType,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private void updateBindingRegion(String objectType, String objectId, Map<String, Object> payload, String inferredRegion) {
        objectScopeBindingRepository.findByObjectTypeAndObjectId(objectType, objectId).ifPresent(binding -> {
            String region = payload.containsKey("regionId") ? stringValue(payload.get("regionId")) : inferredRegion;
            if (region != null && !region.isBlank()) {
                binding.setRegionId(region);
            }
            binding.setUpdatedAt(LocalDateTime.now());
            objectScopeBindingRepository.save(binding);
        });
    }

    private String inferRegionFromBindings(String primaryType, String primaryId, String secondaryType, String secondaryId) {
        return objectScopeBindingRepository.findByObjectTypeAndObjectId(primaryType, primaryId)
                .map(ObjectScopeBindingEntity::getRegionId)
                .or(() -> objectScopeBindingRepository.findByObjectTypeAndObjectId(secondaryType, secondaryId).map(ObjectScopeBindingEntity::getRegionId))
                .orElse(null);
    }

    private NodeEntity requireNode(String nodeId) {
        return nodeRepository.findById(nodeId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.NODE_NOT_FOUND, HttpStatus.NOT_FOUND, "Node not found: " + nodeId));
    }

    private SegmentEntity requireSegment(String segmentId) {
        return segmentRepository.findById(segmentId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.SEGMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Segment not found: " + segmentId));
    }

    private FacilityEntity requireFacility(String facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Facility not found: " + facilityId));
    }

    private void validatePayload(String objectType, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("payload must not be empty");
        }
        Set<String> allowedKeys = switch (objectType) {
            case ObjectScopeService.OBJECT_NODE -> Set.of("nodeName", "nodeType", "status", "authoritySrid", "displaySrid", "geometry2d", "zTop", "zBottom", "buryDepth", "elevationRef", "regionId");
            case ObjectScopeService.OBJECT_SEGMENT -> Set.of("segmentName", "segmentType", "status", "startNodeId", "endNodeId", "lengthMeter", "regionId");
            case ObjectScopeService.OBJECT_FACILITY -> Set.of("facilityName", "facilityType", "status", "segmentId", "nodeId", "positionMeter", "regionId");
            case ObjectScopeService.OBJECT_DEVICE -> Set.of("deviceName", "protocolType", "status", "facilityId", "segmentId", "nodeId", "lastHeartbeat", "regionId");
            case ObjectScopeService.OBJECT_STATION -> Set.of("stationName", "status", "provinceAdcode", "cityAdcode", "authoritySrid", "displaySrid", "longitude", "latitude", "stationInfo", "pipelineLayout", "regionId");
            default -> throw new IllegalArgumentException("Unsupported object type: " + objectType);
        };
        for (String key : payload.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("Unsupported payload field: " + key);
            }
        }
        if (ObjectScopeService.OBJECT_STATION.equals(objectType)) {
            validateStationPayload(payload);
        }
    }

    private void validateStationPayload(Map<String, Object> payload) {
        if (payload.containsKey("longitude")) {
            BigDecimal value = new BigDecimal(String.valueOf(payload.get("longitude")));
            if (value.compareTo(new BigDecimal("-180")) < 0 || value.compareTo(new BigDecimal("180")) > 0) {
                throw new IllegalArgumentException("longitude must be between -180 and 180");
            }
        }
        if (payload.containsKey("latitude")) {
            BigDecimal value = new BigDecimal(String.valueOf(payload.get("latitude")));
            if (value.compareTo(new BigDecimal("-90")) < 0 || value.compareTo(new BigDecimal("90")) > 0) {
                throw new IllegalArgumentException("latitude must be between -90 and 90");
            }
        }
        if (payload.containsKey("stationInfo") && !(payload.get("stationInfo") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("stationInfo must be a JSON object");
        }
        if (payload.containsKey("pipelineLayout")) {
            validatePipelineLayout(payload.get("pipelineLayout"));
        }
    }

    private void validatePipelineLayout(Object value) {
        if (!(value instanceof Map<?, ?> layout) || !"FeatureCollection".equals(layout.get("type"))) {
            throw new IllegalArgumentException("pipelineLayout must be a GeoJSON FeatureCollection");
        }
        if (!(layout.get("features") instanceof List<?> features)) {
            throw new IllegalArgumentException("pipelineLayout.features must be an array");
        }
        Set<String> allowedGeometryTypes = Set.of("Point", "LineString", "MultiLineString");
        for (Object item : features) {
            if (!(item instanceof Map<?, ?> feature) || !"Feature".equals(feature.get("type"))) {
                throw new IllegalArgumentException("pipelineLayout contains an invalid feature");
            }
            Object geometryValue = feature.get("geometry");
            if (!(geometryValue instanceof Map<?, ?> geometry) || !allowedGeometryTypes.contains(String.valueOf(geometry.get("type"))) || geometry.get("coordinates") == null) {
                throw new IllegalArgumentException("pipelineLayout contains an unsupported geometry");
            }
        }
    }

    private MasterChangeResponse toResponse(MasterChangeRequestEntity entity) {
        return new MasterChangeResponse(
                entity.getRequestId(),
                entity.getObjectType(),
                entity.getObjectId(),
                entity.getRequestStatus(),
                entity.getRequestedBy(),
                entity.getApprovedBy(),
                entity.getBaseVersionNo(),
                entity.getEffectiveVersionNo(),
                entity.getReason(),
                readJsonMap(entity.getRequestPayloadJson()),
                entity.getCreatedAt(),
                entity.getApprovedAt(),
                entity.getUpdatedAt()
        );
    }

    private void audit(
            String objectType,
            String objectId,
            String changeRequestId,
            String action,
            String outcome,
            String operatorUserId,
            String beforeSnapshot,
            String afterSnapshot
    ) {
        masterDataAuditRepository.save(new MasterDataAuditEntity(
                null,
                objectType,
                objectId,
                changeRequestId,
                action,
                outcome,
                operatorUserId,
                beforeSnapshot,
                afterSnapshot,
                TraceIdContext.currentOrGenerate(),
                LocalDateTime.now()
        ));
    }

    private String snapshot(Object entity) {
        return toJson(objectMapper.convertValue(entity, new TypeReference<Map<String, Object>>() {
        }));
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse change request payload", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize master data payload", ex);
        }
    }

    private void applyString(Map<String, Object> payload, String key, java.util.function.Consumer<String> consumer) {
        if (payload.containsKey(key)) {
            consumer.accept(stringValue(payload.get(key)));
        }
    }

    private void applyBigDecimal(Map<String, Object> payload, String key, java.util.function.Consumer<BigDecimal> consumer) {
        if (payload.containsKey(key) && payload.get(key) != null) {
            consumer.accept(new BigDecimal(String.valueOf(payload.get(key))));
        }
    }

    private String readRequiredReference(Map<String, Object> payload, String key) {
        String value = stringValue(payload.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeObjectType(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case ObjectScopeService.OBJECT_NODE, ObjectScopeService.OBJECT_SEGMENT, ObjectScopeService.OBJECT_FACILITY, ObjectScopeService.OBJECT_DEVICE, ObjectScopeService.OBJECT_STATION -> normalized;
            default -> throw new IllegalArgumentException("Unsupported object type: " + value);
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private AuthFlowException conflict(String message) {
        return new AuthFlowException(ErrorCode.MASTER_DATA_VERSION_CONFLICT, HttpStatus.CONFLICT, message);
    }

    private record VersionedObject(Object entity, Long versionNo) {
    }
}
