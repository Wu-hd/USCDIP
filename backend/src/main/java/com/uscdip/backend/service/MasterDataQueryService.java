package com.uscdip.backend.service;

import com.uscdip.backend.dto.MasterDataRecordResponse;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.FacilityEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.FacilityRepository;
import com.uscdip.backend.repository.NodeRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.SegmentRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MasterDataQueryService {

    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";

    private final NodeRepository nodeRepository;
    private final SegmentRepository segmentRepository;
    private final FacilityRepository facilityRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final ObjectScopeService objectScopeService;
    private final ObjectChainTraversalService objectChainTraversalService;

    public MasterDataQueryService(
            NodeRepository nodeRepository,
            SegmentRepository segmentRepository,
            FacilityRepository facilityRepository,
            DeviceRepository deviceRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            ObjectScopeService objectScopeService,
            ObjectChainTraversalService objectChainTraversalService
    ) {
        this.nodeRepository = nodeRepository;
        this.segmentRepository = segmentRepository;
        this.facilityRepository = facilityRepository;
        this.deviceRepository = deviceRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.objectScopeService = objectScopeService;
        this.objectChainTraversalService = objectChainTraversalService;
    }

    public PageResponse<MasterDataRecordResponse> getNodes(
            AuthorizationContext context,
            int page,
            int pageSize,
            String status,
            String regionId,
            String segmentId
    ) {
        List<NodeEntity> nodes = nodeRepository.findAll();
        if (segmentId != null && !segmentId.isBlank()) {
            Set<String> relatedNodeIds = Set.copyOf(objectChainTraversalService.findNodeIdsForSegment(segmentId));
            nodes = nodes.stream().filter(node -> relatedNodeIds.contains(node.getNodeId())).toList();
        }
        return toPage(
                context,
                nodes,
                ObjectScopeService.OBJECT_NODE,
                NodeEntity::getNodeId,
                NodeEntity::getStatus,
                this::toNodeResponse,
                page,
                pageSize,
                status,
                regionId
        );
    }

    public Optional<MasterDataRecordResponse> getNodeDetail(AuthorizationContext context, String nodeId) {
        return nodeRepository.findById(nodeId)
                .map(node -> requireAndMap(context, ObjectScopeService.OBJECT_NODE, node.getNodeId(), () -> toNodeResponse(node)));
    }

    public PageResponse<MasterDataRecordResponse> getSegments(
            AuthorizationContext context,
            int page,
            int pageSize,
            String status,
            String regionId,
            String nodeId
    ) {
        List<SegmentEntity> segments = segmentRepository.findAll();
        if (nodeId != null && !nodeId.isBlank()) {
            Set<String> relatedSegmentIds = Set.copyOf(objectChainTraversalService.findSegmentIdsForNode(nodeId));
            segments = segments.stream().filter(segment -> relatedSegmentIds.contains(segment.getSegmentId())).toList();
        }
        return toPage(
                context,
                segments,
                ObjectScopeService.OBJECT_SEGMENT,
                SegmentEntity::getSegmentId,
                SegmentEntity::getStatus,
                this::toSegmentResponse,
                page,
                pageSize,
                status,
                regionId
        );
    }

    public Optional<MasterDataRecordResponse> getSegmentDetail(AuthorizationContext context, String segmentId) {
        return segmentRepository.findById(segmentId)
                .map(segment -> requireAndMap(context, ObjectScopeService.OBJECT_SEGMENT, segment.getSegmentId(), () -> toSegmentResponse(segment)));
    }

    public PageResponse<MasterDataRecordResponse> getFacilities(
            AuthorizationContext context,
            int page,
            int pageSize,
            String status,
            String regionId,
            String segmentId,
            String nodeId
    ) {
        List<FacilityEntity> facilities = facilityRepository.findAll();
        if (segmentId != null && !segmentId.isBlank()) {
            facilities = facilities.stream().filter(facility -> segmentId.equalsIgnoreCase(facility.getSegmentId())).toList();
        }
        if (nodeId != null && !nodeId.isBlank()) {
            facilities = facilities.stream().filter(facility -> nodeId.equalsIgnoreCase(facility.getNodeId())).toList();
        }
        return toPage(
                context,
                facilities,
                ObjectScopeService.OBJECT_FACILITY,
                FacilityEntity::getFacilityId,
                FacilityEntity::getStatus,
                this::toFacilityResponse,
                page,
                pageSize,
                status,
                regionId
        );
    }

    public Optional<MasterDataRecordResponse> getFacilityDetail(AuthorizationContext context, String facilityId) {
        return facilityRepository.findById(facilityId)
                .map(facility -> requireAndMap(context, ObjectScopeService.OBJECT_FACILITY, facility.getFacilityId(), () -> toFacilityResponse(facility)));
    }

    public PageResponse<MasterDataRecordResponse> getDevices(
            AuthorizationContext context,
            int page,
            int pageSize,
            String status,
            String regionId,
            String segmentId,
            String nodeId,
            String facilityId
    ) {
        List<DeviceEntity> devices = deviceRepository.findAll();
        if (segmentId != null && !segmentId.isBlank()) {
            devices = devices.stream().filter(device -> segmentId.equalsIgnoreCase(device.getSegmentId())).toList();
        }
        if (nodeId != null && !nodeId.isBlank()) {
            devices = devices.stream().filter(device -> nodeId.equalsIgnoreCase(device.getNodeId())).toList();
        }
        if (facilityId != null && !facilityId.isBlank()) {
            devices = devices.stream().filter(device -> facilityId.equalsIgnoreCase(device.getFacilityId())).toList();
        }
        return toPage(
                context,
                devices,
                ObjectScopeService.OBJECT_DEVICE,
                DeviceEntity::getDeviceId,
                DeviceEntity::getStatus,
                this::toDeviceResponse,
                page,
                pageSize,
                status,
                regionId
        );
    }

    public Optional<MasterDataRecordResponse> getDeviceDetail(AuthorizationContext context, String deviceId) {
        return deviceRepository.findById(deviceId)
                .map(device -> requireAndMap(context, ObjectScopeService.OBJECT_DEVICE, device.getDeviceId(), () -> toDeviceResponse(device)));
    }

    private <T> PageResponse<MasterDataRecordResponse> toPage(
            AuthorizationContext context,
            List<T> source,
            String objectType,
            Function<T, String> idExtractor,
            Function<T, String> statusExtractor,
            Function<T, MasterDataRecordResponse> mapper,
            int page,
            int pageSize,
            String status,
            String regionId
    ) {
        List<T> filtered = filterAccessible(context, source, objectType, idExtractor, statusExtractor, status, regionId).stream()
                .sorted(Comparator.comparing(idExtractor))
                .toList();

        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= filtered.size()) {
            return PageResponse.of(List.of(), filtered.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        List<MasterDataRecordResponse> items = filtered.subList(fromIndex, toIndex).stream()
                .map(mapper)
                .toList();
        return PageResponse.of(items, filtered.size(), safePage, safePageSize);
    }

    private <T> List<T> filterAccessible(
            AuthorizationContext context,
            List<T> source,
            String objectType,
            Function<T, String> idExtractor,
            Function<T, String> statusExtractor,
            String status,
            String regionId
    ) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<String> objectIds = source.stream().map(idExtractor).toList();
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(context, objectType, objectIds, MENU_ASSET_READ);
        Map<String, ObjectScopeBindingEntity> bindings = loadBindings(objectType, accessibleIds);
        String normalizedStatus = normalize(status);
        String normalizedRegionId = normalize(regionId);

        return source.stream()
                .filter(item -> accessibleIds.contains(idExtractor.apply(item)))
                .filter(item -> normalizedStatus.isEmpty() || normalize(statusExtractor.apply(item)).equals(normalizedStatus))
                .filter(item -> normalizedRegionId.isEmpty() || normalizedRegionId.equals(normalize(bindings.get(idExtractor.apply(item)).getRegionId())))
                .toList();
    }

    private Map<String, ObjectScopeBindingEntity> loadBindings(String objectType, Collection<String> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return Map.of();
        }
        return objectScopeBindingRepository.findByObjectTypeAndObjectIdIn(objectType, objectIds).stream()
                .collect(Collectors.toMap(ObjectScopeBindingEntity::getObjectId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private MasterDataRecordResponse requireAndMap(
            AuthorizationContext context,
            String objectType,
            String objectId,
            java.util.function.Supplier<MasterDataRecordResponse> supplier
    ) {
        objectScopeService.requireAccess(context, objectType, objectId, MENU_ASSET_READ);
        return supplier.get();
    }

    private MasterDataRecordResponse toNodeResponse(NodeEntity node) {
        List<String> segmentIds = objectChainTraversalService.findSegmentIdsForNode(node.getNodeId());
        List<String> facilityIds = objectChainTraversalService.findFacilityIdsForNode(node.getNodeId());
        List<String> deviceIds = objectChainTraversalService.findDeviceIdsForFacilityIds(facilityIds);
        return new MasterDataRecordResponse(
                "NODE",
                node.getNodeId(),
                node.getNodeName(),
                node.getStatus(),
                regionFor(ObjectScopeService.OBJECT_NODE, node.getNodeId()),
                related("segmentIds", segmentIds, "facilityIds", facilityIds, "deviceIds", deviceIds),
                attributes(
                        "nodeType", node.getNodeType(),
                        "authoritySrid", node.getAuthoritySrid(),
                        "displaySrid", node.getDisplaySrid(),
                        "geometry2d", node.getGeometry2d(),
                        "zTop", node.getZTop(),
                        "zBottom", node.getZBottom(),
                        "buryDepth", node.getBuryDepth(),
                        "elevationRef", node.getElevationRef()
                )
        );
    }

    private MasterDataRecordResponse toSegmentResponse(SegmentEntity segment) {
        List<String> facilityIds = objectChainTraversalService.findFacilityIdsForSegment(segment.getSegmentId());
        List<String> deviceIds = objectChainTraversalService.findDeviceIdsForFacilityIds(facilityIds);
        return new MasterDataRecordResponse(
                "SEGMENT",
                segment.getSegmentId(),
                segment.getSegmentName(),
                segment.getStatus(),
                regionFor(ObjectScopeService.OBJECT_SEGMENT, segment.getSegmentId()),
                related(
                        "nodeIds",
                        fallbackIfEmpty(objectChainTraversalService.findNodeIdsForSegment(segment.getSegmentId()), List.of(segment.getStartNodeId(), segment.getEndNodeId())),
                        "facilityIds",
                        facilityIds,
                        "deviceIds",
                        deviceIds
                ),
                attributes(
                        "segmentType", segment.getSegmentType(),
                        "startNodeId", segment.getStartNodeId(),
                        "endNodeId", segment.getEndNodeId(),
                        "lengthMeter", segment.getLengthMeter()
                )
        );
    }

    private MasterDataRecordResponse toFacilityResponse(FacilityEntity facility) {
        return new MasterDataRecordResponse(
                "FACILITY",
                facility.getFacilityId(),
                facility.getFacilityName(),
                facility.getStatus(),
                regionFor(ObjectScopeService.OBJECT_FACILITY, facility.getFacilityId()),
                related(
                        "segmentIds",
                        List.of(facility.getSegmentId()),
                        "nodeIds",
                        List.of(facility.getNodeId()),
                        "deviceIds",
                        objectChainTraversalService.findDeviceIdsForFacility(facility.getFacilityId())
                ),
                attributes(
                        "facilityType", facility.getFacilityType(),
                        "positionMeter", facility.getPositionMeter()
                )
        );
    }

    private MasterDataRecordResponse toDeviceResponse(DeviceEntity device) {
        return new MasterDataRecordResponse(
                "DEVICE",
                device.getDeviceId(),
                device.getDeviceName(),
                device.getStatus(),
                regionFor(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId()),
                related(
                        "facilityIds",
                        List.of(device.getFacilityId()),
                        "segmentIds",
                        List.of(device.getSegmentId()),
                        "nodeIds",
                        List.of(device.getNodeId())
                ),
                attributes(
                        "protocolType", device.getProtocolType(),
                        "lastHeartbeat", device.getLastHeartbeat()
                )
        );
    }

    private String regionFor(String objectType, String objectId) {
        return objectScopeBindingRepository.findByObjectTypeAndObjectId(objectType, objectId)
                .map(ObjectScopeBindingEntity::getRegionId)
                .orElse(null);
    }

    private Map<String, List<String>> related(String firstKey, List<String> firstValue, String secondKey, List<String> secondValue, String thirdKey, List<String> thirdValue) {
        Map<String, List<String>> related = new LinkedHashMap<>();
        related.put(firstKey, List.copyOf(firstValue));
        related.put(secondKey, List.copyOf(secondValue));
        related.put(thirdKey, List.copyOf(thirdValue));
        return related;
    }

    private Map<String, Object> attributes(Object... keyValues) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            attributes.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return attributes;
    }

    private List<String> fallbackIfEmpty(List<String> preferred, List<String> fallback) {
        return preferred == null || preferred.isEmpty() ? fallback : preferred;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
