package com.uscdip.backend.service;

import com.uscdip.backend.dto.GisBboxResponse;
import com.uscdip.backend.dto.GisObjectPickRequest;
import com.uscdip.backend.dto.GisObjectPickResponse;
import com.uscdip.backend.dto.GisObjectRecordResponse;
import com.uscdip.backend.dto.GisPointResponse;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.FacilityEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.MonitoringStationEntity;
import com.uscdip.backend.entity.ObjectGeoIndexEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.FacilityRepository;
import com.uscdip.backend.repository.NodeRepository;
import com.uscdip.backend.repository.MonitoringStationRepository;
import com.uscdip.backend.repository.ObjectGeoIndexRepository;
import com.uscdip.backend.repository.SegmentRepository;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class GisSpatialQueryService {

    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final BigDecimal DEFAULT_PICK_TOLERANCE_METERS = new BigDecimal("25.00");

    private final ObjectGeoIndexRepository objectGeoIndexRepository;
    private final ObjectScopeService objectScopeService;
    private final ObjectChainTraversalService objectChainTraversalService;
    private final NodeRepository nodeRepository;
    private final SegmentRepository segmentRepository;
    private final FacilityRepository facilityRepository;
    private final DeviceRepository deviceRepository;
    private final MonitoringStationRepository monitoringStationRepository;
    private final GisCoordinateService gisCoordinateService;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public GisSpatialQueryService(
            ObjectGeoIndexRepository objectGeoIndexRepository,
            ObjectScopeService objectScopeService,
            ObjectChainTraversalService objectChainTraversalService,
            NodeRepository nodeRepository,
            SegmentRepository segmentRepository,
            FacilityRepository facilityRepository,
            DeviceRepository deviceRepository,
            MonitoringStationRepository monitoringStationRepository,
            GisCoordinateService gisCoordinateService
    ) {
        this.objectGeoIndexRepository = objectGeoIndexRepository;
        this.objectScopeService = objectScopeService;
        this.objectChainTraversalService = objectChainTraversalService;
        this.nodeRepository = nodeRepository;
        this.segmentRepository = segmentRepository;
        this.facilityRepository = facilityRepository;
        this.deviceRepository = deviceRepository;
        this.monitoringStationRepository = monitoringStationRepository;
        this.gisCoordinateService = gisCoordinateService;
    }

    public PageResponse<GisObjectRecordResponse> queryByBbox(
            AuthorizationContext context,
            BigDecimal minX,
            BigDecimal minY,
            BigDecimal maxX,
            BigDecimal maxY,
            String authoritySrid,
            String displaySrid,
            String objectType,
            int page,
            int pageSize
    ) {
        validateBbox(minX, minY, maxX, maxY);
        String requestedAuthority = normalizeSrid(authoritySrid);
        String requestedDisplay = normalizeDisplaySrid(displaySrid);
        List<ObjectGeoIndexEntity> filtered = accessibleIndexes(context, resolveObjectTypes(objectType)).stream()
                .filter(index -> intersectsBBox(index, minX, minY, maxX, maxY, requestedAuthority))
                .sorted(Comparator.comparing(ObjectGeoIndexEntity::getObjectType).thenComparing(ObjectGeoIndexEntity::getObjectId))
                .toList();

        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= filtered.size()) {
            return PageResponse.of(List.of(), filtered.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        List<GisObjectRecordResponse> items = filtered.subList(fromIndex, toIndex).stream()
                .map(index -> toRecord(index, requestedDisplay))
                .toList();
        return PageResponse.of(items, filtered.size(), safePage, safePageSize);
    }

    public Optional<GisObjectPickResponse> pickObject(AuthorizationContext context, GisObjectPickRequest request) {
        BigDecimal toleranceMeters = request.toleranceMeters() == null
                ? DEFAULT_PICK_TOLERANCE_METERS
                : request.toleranceMeters().setScale(2, RoundingMode.HALF_UP);
        String requestedDisplay = normalizeDisplaySrid(request.displaySrid());
        Point queryPoint = geometryFactory.createPoint(toCoordinate(
                gisCoordinateService.convertPoint(request.x(), request.y(), request.authoritySrid(), "EPSG:3857")
        ));

        return accessibleIndexes(context, resolveObjectTypes(request.objectTypes())).stream()
                .map(index -> new PickCandidate(index, distanceMeters(index, queryPoint)))
                .filter(candidate -> candidate.distanceMeters().compareTo(toleranceMeters) <= 0)
                .min(Comparator.comparing(PickCandidate::distanceMeters)
                        .thenComparing(candidate -> candidate.index().getObjectType())
                        .thenComparing(candidate -> candidate.index().getObjectId()))
                .map(candidate -> new GisObjectPickResponse(
                        toRecord(candidate.index(), requestedDisplay),
                        candidate.distanceMeters(),
                        toleranceMeters
                ));
    }

    public Optional<GisObjectRecordResponse> getObjectDetail(
            AuthorizationContext context,
            String objectType,
            String objectId,
            String displaySrid
    ) {
        String normalizedType = normalizeObjectType(objectType);
        objectScopeService.requireAccess(context, normalizedType, objectId, MENU_ASSET_READ);
        String requestedDisplay = normalizeDisplaySrid(displaySrid);
        return objectGeoIndexRepository.findByObjectTypeAndObjectId(normalizedType, objectId)
                .map(index -> toRecord(index, requestedDisplay));
    }

    private List<ObjectGeoIndexEntity> accessibleIndexes(AuthorizationContext context, Collection<String> objectTypes) {
        List<ObjectGeoIndexEntity> indexes = objectTypes == null || objectTypes.isEmpty()
                ? objectGeoIndexRepository.findAll()
                : objectGeoIndexRepository.findByObjectTypeIn(objectTypes);
        Map<String, List<ObjectGeoIndexEntity>> grouped = indexes.stream()
                .collect(Collectors.groupingBy(ObjectGeoIndexEntity::getObjectType, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .flatMap(entry -> {
                    Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                            context,
                            entry.getKey(),
                            entry.getValue().stream().map(ObjectGeoIndexEntity::getObjectId).toList(),
                            MENU_ASSET_READ
                    );
                    return entry.getValue().stream().filter(index -> accessibleIds.contains(index.getObjectId()));
                })
                .toList();
    }

    private boolean intersectsBBox(
            ObjectGeoIndexEntity index,
            BigDecimal minX,
            BigDecimal minY,
            BigDecimal maxX,
            BigDecimal maxY,
            String requestedAuthority
    ) {
        BigDecimal[] converted = gisCoordinateService.convertEnvelope(
                minX,
                minY,
                maxX,
                maxY,
                requestedAuthority,
                index.getAuthoritySrid()
        );
        Envelope queryEnvelope = new Envelope(
                converted[0].doubleValue(),
                converted[2].doubleValue(),
                converted[1].doubleValue(),
                converted[3].doubleValue()
        );
        Envelope objectEnvelope = new Envelope(
                index.getBboxMinX().doubleValue(),
                index.getBboxMaxX().doubleValue(),
                index.getBboxMinY().doubleValue(),
                index.getBboxMaxY().doubleValue()
        );
        return objectEnvelope.intersects(queryEnvelope);
    }

    private BigDecimal distanceMeters(ObjectGeoIndexEntity index, Point queryPoint3857) {
        String geometry3857 = gisCoordinateService.convertGeometryWkt(index.getAuthoritySrid(), "EPSG:3857", index.getGeometry2d());
        Geometry candidate = gisCoordinateService.readGeometry(geometry3857);
        return BigDecimal.valueOf(candidate.distance(queryPoint3857)).setScale(2, RoundingMode.HALF_UP);
    }

    private GisObjectRecordResponse toRecord(ObjectGeoIndexEntity index, String requestedDisplaySrid) {
        String geometry = gisCoordinateService.convertGeometryWkt(index.getAuthoritySrid(), requestedDisplaySrid, index.getGeometry2d());
        BigDecimal[] anchor = gisCoordinateService.convertPoint(index.getAnchorX(), index.getAnchorY(), index.getAuthoritySrid(), requestedDisplaySrid);
        BigDecimal[] bbox = gisCoordinateService.convertEnvelope(
                index.getBboxMinX(),
                index.getBboxMinY(),
                index.getBboxMaxX(),
                index.getBboxMaxY(),
                index.getAuthoritySrid(),
                requestedDisplaySrid
        );

        return switch (index.getObjectType()) {
            case ObjectScopeService.OBJECT_NODE -> nodeRepository.findById(index.getObjectId())
                    .map(node -> new GisObjectRecordResponse(
                            ObjectScopeService.OBJECT_NODE,
                            node.getNodeId(),
                            node.getNodeName(),
                            index.getRegionId(),
                            index.getAuthoritySrid(),
                            requestedDisplaySrid,
                            geometry,
                            new GisPointResponse(anchor[0], anchor[1]),
                            new GisBboxResponse(bbox[0], bbox[1], bbox[2], bbox[3]),
                            related(
                                    "segmentIds",
                                    objectChainTraversalService.findSegmentIdsForNode(node.getNodeId()),
                                    "facilityIds",
                                    objectChainTraversalService.findFacilityIdsForNode(node.getNodeId()),
                                    "deviceIds",
                                    objectChainTraversalService.findDeviceIdsForFacilityIds(objectChainTraversalService.findFacilityIdsForNode(node.getNodeId()))
                            ),
                            attributes(
                                    "status", node.getStatus(),
                                    "nodeType", node.getNodeType(),
                                    "versionNo", node.getVersionNo(),
                                    "sourceGeometry2d", node.getGeometry2d(),
                                    "zTop", node.getZTop(),
                                    "zBottom", node.getZBottom(),
                                    "buryDepth", node.getBuryDepth(),
                                    "elevationRef", node.getElevationRef()
                            )
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Node not found for GIS object: " + index.getObjectId()));
            case ObjectScopeService.OBJECT_SEGMENT -> segmentRepository.findById(index.getObjectId())
                    .map(segment -> new GisObjectRecordResponse(
                            ObjectScopeService.OBJECT_SEGMENT,
                            segment.getSegmentId(),
                            segment.getSegmentName(),
                            index.getRegionId(),
                            index.getAuthoritySrid(),
                            requestedDisplaySrid,
                            geometry,
                            new GisPointResponse(anchor[0], anchor[1]),
                            new GisBboxResponse(bbox[0], bbox[1], bbox[2], bbox[3]),
                            related(
                                    "nodeIds",
                                    objectChainTraversalService.findNodeIdsForSegment(segment.getSegmentId()),
                                    "facilityIds",
                                    objectChainTraversalService.findFacilityIdsForSegment(segment.getSegmentId()),
                                    "deviceIds",
                                    objectChainTraversalService.findDeviceIdsForFacilityIds(objectChainTraversalService.findFacilityIdsForSegment(segment.getSegmentId()))
                            ),
                            attributes(
                                    "status", segment.getStatus(),
                                    "segmentType", segment.getSegmentType(),
                                    "versionNo", segment.getVersionNo(),
                                    "startNodeId", segment.getStartNodeId(),
                                    "endNodeId", segment.getEndNodeId(),
                                    "lengthMeter", segment.getLengthMeter()
                            )
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Segment not found for GIS object: " + index.getObjectId()));
            case ObjectScopeService.OBJECT_FACILITY -> facilityRepository.findById(index.getObjectId())
                    .map(facility -> new GisObjectRecordResponse(
                            ObjectScopeService.OBJECT_FACILITY,
                            facility.getFacilityId(),
                            facility.getFacilityName(),
                            index.getRegionId(),
                            index.getAuthoritySrid(),
                            requestedDisplaySrid,
                            geometry,
                            new GisPointResponse(anchor[0], anchor[1]),
                            new GisBboxResponse(bbox[0], bbox[1], bbox[2], bbox[3]),
                            related(
                                    "segmentIds",
                                    List.of(facility.getSegmentId()),
                                    "nodeIds",
                                    List.of(facility.getNodeId()),
                                    "deviceIds",
                                    objectChainTraversalService.findDeviceIdsForFacility(facility.getFacilityId())
                            ),
                            attributes(
                                    "status", facility.getStatus(),
                                    "facilityType", facility.getFacilityType(),
                                    "versionNo", facility.getVersionNo(),
                                    "positionMeter", facility.getPositionMeter()
                            )
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Facility not found for GIS object: " + index.getObjectId()));
            case ObjectScopeService.OBJECT_DEVICE -> deviceRepository.findById(index.getObjectId())
                    .map(device -> new GisObjectRecordResponse(
                            ObjectScopeService.OBJECT_DEVICE,
                            device.getDeviceId(),
                            device.getDeviceName(),
                            index.getRegionId(),
                            index.getAuthoritySrid(),
                            requestedDisplaySrid,
                            geometry,
                            new GisPointResponse(anchor[0], anchor[1]),
                            new GisBboxResponse(bbox[0], bbox[1], bbox[2], bbox[3]),
                            related(
                                    "facilityIds",
                                    List.of(device.getFacilityId()),
                                    "segmentIds",
                                    List.of(device.getSegmentId()),
                                    "nodeIds",
                                    List.of(device.getNodeId())
                            ),
                            attributes(
                                    "status", device.getStatus(),
                                    "protocolType", device.getProtocolType(),
                                    "versionNo", device.getVersionNo(),
                                    "lastHeartbeat", device.getLastHeartbeat()
                            )
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Device not found for GIS object: " + index.getObjectId()));
            case ObjectScopeService.OBJECT_STATION -> monitoringStationRepository.findById(index.getObjectId())
                    .map(station -> new GisObjectRecordResponse(
                            ObjectScopeService.OBJECT_STATION,
                            station.getStationId(),
                            station.getStationName(),
                            index.getRegionId(),
                            index.getAuthoritySrid(),
                            requestedDisplaySrid,
                            geometry,
                            new GisPointResponse(anchor[0], anchor[1]),
                            new GisBboxResponse(bbox[0], bbox[1], bbox[2], bbox[3]),
                            Map.of(),
                            attributes(
                                    "status", station.getStatus(),
                                    "provinceAdcode", station.getProvinceAdcode(),
                                    "cityAdcode", station.getCityAdcode(),
                                    "versionNo", station.getVersionNo(),
                                    "updatedAt", station.getUpdatedAt()
                            )
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("Station not found for GIS object: " + index.getObjectId()));
            default -> throw new IllegalArgumentException("Unsupported GIS object type: " + index.getObjectType());
        };
    }

    private void validateBbox(BigDecimal minX, BigDecimal minY, BigDecimal maxX, BigDecimal maxY) {
        if (minX == null || minY == null || maxX == null || maxY == null) {
            throw new IllegalArgumentException("minX, minY, maxX and maxY are required");
        }
        if (minX.compareTo(maxX) > 0 || minY.compareTo(maxY) > 0) {
            throw new IllegalArgumentException("bbox min values must be less than or equal to max values");
        }
    }

    private List<String> resolveObjectTypes(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return List.of(
                    ObjectScopeService.OBJECT_NODE,
                    ObjectScopeService.OBJECT_SEGMENT,
                    ObjectScopeService.OBJECT_FACILITY,
                    ObjectScopeService.OBJECT_DEVICE,
                    ObjectScopeService.OBJECT_STATION
            );
        }
        return List.of(normalizeObjectType(objectType));
    }

    private List<String> resolveObjectTypes(List<String> objectTypes) {
        if (objectTypes == null || objectTypes.isEmpty()) {
            return resolveObjectTypes((String) null);
        }
        return objectTypes.stream()
                .map(this::normalizeObjectType)
                .distinct()
                .toList();
    }

    private String normalizeObjectType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case ObjectScopeService.OBJECT_NODE,
                    ObjectScopeService.OBJECT_SEGMENT,
                    ObjectScopeService.OBJECT_FACILITY,
                    ObjectScopeService.OBJECT_DEVICE,
                    ObjectScopeService.OBJECT_STATION -> normalized;
            default -> throw new IllegalArgumentException("Unsupported GIS object type: " + value);
        };
    }

    private String normalizeSrid(String value) {
        return value == null || value.isBlank() ? "EPSG:4490" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDisplaySrid(String value) {
        return value == null || value.isBlank() ? "EPSG:3857" : value.trim().toUpperCase(Locale.ROOT);
    }

    private org.locationtech.jts.geom.Coordinate toCoordinate(BigDecimal[] xy) {
        return new org.locationtech.jts.geom.Coordinate(xy[0].doubleValue(), xy[1].doubleValue());
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

    private record PickCandidate(ObjectGeoIndexEntity index, BigDecimal distanceMeters) {
    }
}
