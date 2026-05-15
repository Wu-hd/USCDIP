package com.uscdip.backend.service;

import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.FacilityEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.ObjectGeoIndexEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.FacilityRepository;
import com.uscdip.backend.repository.NodeRepository;
import com.uscdip.backend.repository.ObjectGeoIndexRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.SegmentRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTWriter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class ObjectGeoIndexService {

    private final ObjectGeoIndexRepository objectGeoIndexRepository;
    private final NodeRepository nodeRepository;
    private final SegmentRepository segmentRepository;
    private final FacilityRepository facilityRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final GisCoordinateService gisCoordinateService;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final WKTWriter wktWriter = new WKTWriter();

    public ObjectGeoIndexService(
            ObjectGeoIndexRepository objectGeoIndexRepository,
            NodeRepository nodeRepository,
            SegmentRepository segmentRepository,
            FacilityRepository facilityRepository,
            DeviceRepository deviceRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            GisCoordinateService gisCoordinateService
    ) {
        this.objectGeoIndexRepository = objectGeoIndexRepository;
        this.nodeRepository = nodeRepository;
        this.segmentRepository = segmentRepository;
        this.facilityRepository = facilityRepository;
        this.deviceRepository = deviceRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.gisCoordinateService = gisCoordinateService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeIfMissing() {
        if (objectGeoIndexRepository.count() == 0 && nodeRepository.count() > 0) {
            rebuildAllIndexes();
        }
    }

    @Transactional
    public void rebuildAllIndexes() {
        nodeRepository.findAll().forEach(node -> refreshObject(ObjectScopeService.OBJECT_NODE, node.getNodeId()));
        segmentRepository.findAll().forEach(segment -> refreshObject(ObjectScopeService.OBJECT_SEGMENT, segment.getSegmentId()));
        facilityRepository.findAll().forEach(facility -> refreshObject(ObjectScopeService.OBJECT_FACILITY, facility.getFacilityId()));
        deviceRepository.findAll().forEach(device -> refreshObject(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId()));
    }

    @Transactional
    public void refreshObjectAndDependents(String objectType, String objectId) {
        String normalizedType = normalize(objectType);
        refreshObject(normalizedType, objectId);
        switch (normalizedType) {
            case ObjectScopeService.OBJECT_NODE -> refreshNodeDependents(objectId);
            case ObjectScopeService.OBJECT_SEGMENT -> refreshSegmentDependents(objectId);
            case ObjectScopeService.OBJECT_FACILITY -> refreshFacilityDependents(objectId);
            case ObjectScopeService.OBJECT_DEVICE -> {
            }
            default -> throw new IllegalArgumentException("Unsupported geo index object type: " + objectType);
        }
    }

    @Transactional
    public void refreshObject(String objectType, String objectId) {
        String normalizedType = normalize(objectType);
        switch (normalizedType) {
            case ObjectScopeService.OBJECT_NODE -> upsertNode(objectId);
            case ObjectScopeService.OBJECT_SEGMENT -> upsertSegment(objectId);
            case ObjectScopeService.OBJECT_FACILITY -> upsertFacility(objectId);
            case ObjectScopeService.OBJECT_DEVICE -> upsertDevice(objectId);
            default -> throw new IllegalArgumentException("Unsupported geo index object type: " + objectType);
        }
    }

    private void refreshNodeDependents(String nodeId) {
        segmentRepository.findAll().stream()
                .filter(segment -> nodeId.equalsIgnoreCase(segment.getStartNodeId()) || nodeId.equalsIgnoreCase(segment.getEndNodeId()))
                .forEach(segment -> refreshObject(ObjectScopeService.OBJECT_SEGMENT, segment.getSegmentId()));
        facilityRepository.findAll().stream()
                .filter(facility -> nodeId.equalsIgnoreCase(facility.getNodeId()))
                .forEach(facility -> refreshObjectAndDependents(ObjectScopeService.OBJECT_FACILITY, facility.getFacilityId()));
        deviceRepository.findAll().stream()
                .filter(device -> nodeId.equalsIgnoreCase(device.getNodeId()))
                .forEach(device -> refreshObject(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId()));
    }

    private void refreshSegmentDependents(String segmentId) {
        facilityRepository.findAll().stream()
                .filter(facility -> segmentId.equalsIgnoreCase(facility.getSegmentId()))
                .forEach(facility -> refreshObjectAndDependents(ObjectScopeService.OBJECT_FACILITY, facility.getFacilityId()));
        deviceRepository.findAll().stream()
                .filter(device -> segmentId.equalsIgnoreCase(device.getSegmentId()))
                .forEach(device -> refreshObject(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId()));
    }

    private void refreshFacilityDependents(String facilityId) {
        deviceRepository.findByFacilityId(facilityId).forEach(device -> refreshObject(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId()));
    }

    private void upsertNode(String nodeId) {
        nodeRepository.findById(nodeId)
                .ifPresentOrElse(this::saveNodeIndex, () -> deleteIndex(ObjectScopeService.OBJECT_NODE, nodeId));
    }

    private void upsertSegment(String segmentId) {
        segmentRepository.findById(segmentId)
                .ifPresentOrElse(this::saveSegmentIndex, () -> deleteIndex(ObjectScopeService.OBJECT_SEGMENT, segmentId));
    }

    private void upsertFacility(String facilityId) {
        facilityRepository.findById(facilityId)
                .ifPresentOrElse(this::saveFacilityIndex, () -> deleteIndex(ObjectScopeService.OBJECT_FACILITY, facilityId));
    }

    private void upsertDevice(String deviceId) {
        deviceRepository.findById(deviceId)
                .ifPresentOrElse(this::saveDeviceIndex, () -> deleteIndex(ObjectScopeService.OBJECT_DEVICE, deviceId));
    }

    private void saveNodeIndex(NodeEntity node) {
        if (node.getGeometry2d() == null || node.getGeometry2d().isBlank()) {
            deleteIndex(ObjectScopeService.OBJECT_NODE, node.getNodeId());
            return;
        }
        String authoritySrid = defaultString(node.getAuthoritySrid(), "EPSG:4490");
        String displaySrid = defaultString(node.getDisplaySrid(), "EPSG:3857");
        Point anchor = gisCoordinateService.geometryAnchor(node.getGeometry2d());
        Envelope envelope = gisCoordinateService.geometryEnvelope(node.getGeometry2d());
        save(buildEntity(
                ObjectScopeService.OBJECT_NODE,
                node.getNodeId(),
                authoritySrid,
                displaySrid,
                node.getGeometry2d(),
                anchor,
                envelope,
                regionFor(ObjectScopeService.OBJECT_NODE, node.getNodeId())
        ));
    }

    private void saveSegmentIndex(SegmentEntity segment) {
        Optional<NodeEntity> startNode = nodeRepository.findById(segment.getStartNodeId());
        Optional<NodeEntity> endNode = nodeRepository.findById(segment.getEndNodeId());
        if (startNode.isEmpty() || endNode.isEmpty() || isBlank(startNode.get().getGeometry2d()) || isBlank(endNode.get().getGeometry2d())) {
            deleteIndex(ObjectScopeService.OBJECT_SEGMENT, segment.getSegmentId());
            return;
        }

        Point startPoint = gisCoordinateService.geometryAnchor(startNode.get().getGeometry2d());
        Point endPoint = gisCoordinateService.geometryAnchor(endNode.get().getGeometry2d());
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(startPoint.getX(), startPoint.getY()),
                new Coordinate(endPoint.getX(), endPoint.getY())
        };
        String geometry = wktWriter.write(geometryFactory.createLineString(new CoordinateArraySequence(coordinates)));
        String authoritySrid = defaultString(startNode.get().getAuthoritySrid(), "EPSG:4490");
        String displaySrid = defaultString(startNode.get().getDisplaySrid(), "EPSG:3857");
        Point anchor = geometryFactory.createLineString(coordinates).getCentroid();
        Envelope envelope = geometryFactory.createLineString(coordinates).getEnvelopeInternal();
        save(buildEntity(
                ObjectScopeService.OBJECT_SEGMENT,
                segment.getSegmentId(),
                authoritySrid,
                displaySrid,
                geometry,
                anchor,
                envelope,
                regionFor(ObjectScopeService.OBJECT_SEGMENT, segment.getSegmentId())
        ));
    }

    private void saveFacilityIndex(FacilityEntity facility) {
        NodeEntity node = nodeRepository.findById(facility.getNodeId()).orElse(null);
        if (node == null || isBlank(node.getGeometry2d())) {
            deleteIndex(ObjectScopeService.OBJECT_FACILITY, facility.getFacilityId());
            return;
        }
        Point anchor = gisCoordinateService.geometryAnchor(node.getGeometry2d());
        Envelope envelope = gisCoordinateService.geometryEnvelope(node.getGeometry2d());
        save(buildEntity(
                ObjectScopeService.OBJECT_FACILITY,
                facility.getFacilityId(),
                defaultString(node.getAuthoritySrid(), "EPSG:4490"),
                defaultString(node.getDisplaySrid(), "EPSG:3857"),
                node.getGeometry2d(),
                anchor,
                envelope,
                regionFor(ObjectScopeService.OBJECT_FACILITY, facility.getFacilityId())
        ));
    }

    private void saveDeviceIndex(DeviceEntity device) {
        NodeEntity node = nodeRepository.findById(device.getNodeId()).orElse(null);
        if (node == null || isBlank(node.getGeometry2d())) {
            deleteIndex(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId());
            return;
        }
        Point anchor = gisCoordinateService.geometryAnchor(node.getGeometry2d());
        Envelope envelope = gisCoordinateService.geometryEnvelope(node.getGeometry2d());
        save(buildEntity(
                ObjectScopeService.OBJECT_DEVICE,
                device.getDeviceId(),
                defaultString(node.getAuthoritySrid(), "EPSG:4490"),
                defaultString(node.getDisplaySrid(), "EPSG:3857"),
                node.getGeometry2d(),
                anchor,
                envelope,
                regionFor(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId())
        ));
    }

    private ObjectGeoIndexEntity buildEntity(
            String objectType,
            String objectId,
            String authoritySrid,
            String displaySrid,
            String geometry,
            Point anchor,
            Envelope envelope,
            String regionId
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new ObjectGeoIndexEntity(
                geoId(objectType, objectId),
                objectType,
                objectId,
                authoritySrid,
                displaySrid,
                geometry,
                scale(anchor.getX()),
                scale(anchor.getY()),
                scale(envelope.getMinX()),
                scale(envelope.getMinY()),
                scale(envelope.getMaxX()),
                scale(envelope.getMaxY()),
                regionId,
                now,
                now
        );
    }

    private void save(ObjectGeoIndexEntity entity) {
        objectGeoIndexRepository.save(entity);
    }

    private void deleteIndex(String objectType, String objectId) {
        objectGeoIndexRepository.findByObjectTypeAndObjectId(objectType, objectId)
                .ifPresent(objectGeoIndexRepository::delete);
    }

    private String regionFor(String objectType, String objectId) {
        return objectScopeBindingRepository.findByObjectTypeAndObjectId(objectType, objectId)
                .map(ObjectScopeBindingEntity::getRegionId)
                .orElse(null);
    }

    private String geoId(String objectType, String objectId) {
        return "OGI-" + normalize(objectType) + "-" + objectId.toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
