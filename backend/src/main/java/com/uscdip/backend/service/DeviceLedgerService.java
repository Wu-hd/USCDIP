package com.uscdip.backend.service;

import com.uscdip.backend.dto.DeviceHeartbeatReportRequest;
import com.uscdip.backend.dto.DeviceLedgerRegisterRequest;
import com.uscdip.backend.dto.DeviceLedgerResponse;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.DeviceHeartbeatEntity;
import com.uscdip.backend.entity.FacilityEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.ObjectRelationEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.DeviceHeartbeatRepository;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.FacilityRepository;
import com.uscdip.backend.repository.NodeRepository;
import com.uscdip.backend.repository.ObjectRelationRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.SegmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeviceLedgerService {

    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String STATUS_WARNING = "WARNING";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final int OFFLINE_THRESHOLD_SECONDS = 90;
    private static final int WARNING_BUFFER_LEVEL = 80;

    private final DeviceRepository deviceRepository;
    private final DeviceHeartbeatRepository deviceHeartbeatRepository;
    private final FacilityRepository facilityRepository;
    private final SegmentRepository segmentRepository;
    private final NodeRepository nodeRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final ObjectRelationRepository objectRelationRepository;
    private final ObjectScopeService objectScopeService;

    public DeviceLedgerService(
            DeviceRepository deviceRepository,
            DeviceHeartbeatRepository deviceHeartbeatRepository,
            FacilityRepository facilityRepository,
            SegmentRepository segmentRepository,
            NodeRepository nodeRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            ObjectRelationRepository objectRelationRepository,
            ObjectScopeService objectScopeService
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceHeartbeatRepository = deviceHeartbeatRepository;
        this.facilityRepository = facilityRepository;
        this.segmentRepository = segmentRepository;
        this.nodeRepository = nodeRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.objectRelationRepository = objectRelationRepository;
        this.objectScopeService = objectScopeService;
    }

    @Transactional
    public PageResponse<DeviceLedgerResponse> getDevices(
            AuthorizationContext context,
            int page,
            int pageSize,
            String status,
            String regionId,
            String segmentId,
            String nodeId,
            String facilityId,
            String protocolType,
            Boolean calibrationExpired
    ) {
        List<DeviceEntity> devices = deviceRepository.findAll().stream()
                .map(this::refreshComputedStatus)
                .filter(device -> matchesFilter(device.getSegmentId(), segmentId))
                .filter(device -> matchesFilter(device.getNodeId(), nodeId))
                .filter(device -> matchesFilter(device.getFacilityId(), facilityId))
                .filter(device -> matchesFilter(device.getProtocolType(), protocolType))
                .toList();

        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_DEVICE,
                devices.stream().map(DeviceEntity::getDeviceId).toList(),
                MENU_ASSET_READ
        );
        Map<String, ObjectScopeBindingEntity> bindings = loadBindings(accessibleIds);

        List<DeviceLedgerResponse> filtered = devices.stream()
                .filter(device -> accessibleIds.contains(device.getDeviceId()))
                .map(device -> toResponse(device, bindings.get(device.getDeviceId())))
                .filter(response -> matchesFilter(response.onlineStatus(), status))
                .filter(response -> matchesFilter(response.regionId(), regionId))
                .filter(response -> calibrationExpired == null || response.calibrationExpired() == calibrationExpired)
                .sorted(Comparator.comparing(DeviceLedgerResponse::deviceId))
                .toList();

        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= filtered.size()) {
            return PageResponse.of(List.of(), filtered.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        return PageResponse.of(filtered.subList(fromIndex, toIndex), filtered.size(), safePage, safePageSize);
    }

    @Transactional
    public Optional<DeviceLedgerResponse> getDeviceDetail(AuthorizationContext context, String deviceId) {
        return deviceRepository.findById(deviceId)
                .map(this::refreshComputedStatus)
                .map(device -> {
                    objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, device.getDeviceId(), MENU_ASSET_READ);
                    ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId(
                                    ObjectScopeService.OBJECT_DEVICE,
                                    device.getDeviceId()
                            )
                            .orElse(null);
                    return toResponse(device, binding);
                });
    }

    @Transactional
    public UpsertResult registerDevice(DeviceLedgerRegisterRequest request) {
        validateDeviceRelation(request.facilityId(), request.segmentId(), request.nodeId());

        LocalDateTime now = LocalDateTime.now();
        Optional<DeviceEntity> existing = deviceRepository.findById(request.deviceId());
        DeviceEntity device = existing.orElseGet(() -> new DeviceEntity());

        if (existing.isEmpty()) {
            device.setDeviceId(request.deviceId().trim());
            device.setCreatedAt(now);
            device.setLastHeartbeat(null);
            device.setLastRecvTime(null);
            device.setLastBufferLevel(null);
            device.setLastAbnormalFlags(null);
            device.setOnlineStatusReason("NO_HEARTBEAT");
            device.setStatus(STATUS_OFFLINE);
        }

        device.setDeviceName(request.deviceName().trim());
        device.setFacilityId(request.facilityId().trim());
        device.setSegmentId(request.segmentId().trim());
        device.setNodeId(request.nodeId().trim());
        device.setProtocolType(request.protocolType().trim().toUpperCase(Locale.ROOT));
        device.setCalibrationDueAt(request.calibrationDueAt());
        device.setUpdatedAt(now);

        DeviceEntity saved = refreshComputedStatus(deviceRepository.save(device));
        syncDeviceBinding(saved);
        syncDeviceRelation(saved);
        DeviceLedgerResponse response = toResponse(saved, objectScopeBindingRepository
                .findByObjectTypeAndObjectId(ObjectScopeService.OBJECT_DEVICE, saved.getDeviceId())
                .orElse(null));
        return new UpsertResult(response, existing.isEmpty());
    }

    @Transactional
    public DeviceLedgerResponse reportHeartbeat(String deviceId, DeviceHeartbeatReportRequest request) {
        if (request.recvTime().isBefore(request.heartbeatTime())) {
            throw invalidHeartbeat("recvTime must be greater than or equal to heartbeatTime");
        }
        if (request.bufferLevel() != null && (request.bufferLevel() < 0 || request.bufferLevel() > 100)) {
            throw invalidHeartbeat("bufferLevel must be between 0 and 100");
        }

        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.DEVICE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Device not found: " + deviceId
                ));

        LocalDateTime now = LocalDateTime.now();
        List<String> abnormalFlags = normalizeFlags(request.abnormalFlags());
        StatusEvaluation evaluation = evaluateStatus(request.heartbeatTime(), request.bufferLevel(), abnormalFlags, now);

        deviceHeartbeatRepository.save(new DeviceHeartbeatEntity(
                "HB-" + UUID.randomUUID(),
                device.getDeviceId(),
                request.heartbeatTime(),
                request.recvTime(),
                request.bufferLevel(),
                encodeFlags(abnormalFlags),
                evaluation.status(),
                now
        ));

        device.setLastHeartbeat(request.heartbeatTime());
        device.setLastRecvTime(request.recvTime());
        device.setLastBufferLevel(request.bufferLevel());
        device.setLastAbnormalFlags(encodeFlags(abnormalFlags));
        device.setStatus(evaluation.status());
        device.setOnlineStatusReason(evaluation.reason());
        device.setUpdatedAt(now);

        DeviceEntity saved = deviceRepository.save(device);
        ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId(
                        ObjectScopeService.OBJECT_DEVICE,
                        saved.getDeviceId()
                )
                .orElse(null);
        return toResponse(saved, binding);
    }

    private DeviceEntity refreshComputedStatus(DeviceEntity device) {
        StatusEvaluation evaluation = evaluateStatus(
                device.getLastHeartbeat(),
                device.getLastBufferLevel(),
                decodeFlags(device.getLastAbnormalFlags()),
                LocalDateTime.now()
        );
        if (!normalize(device.getStatus()).equals(evaluation.status())
                || !normalize(device.getOnlineStatusReason()).equals(evaluation.reason())) {
            device.setStatus(evaluation.status());
            device.setOnlineStatusReason(evaluation.reason());
            device.setUpdatedAt(LocalDateTime.now());
            return deviceRepository.save(device);
        }
        return device;
    }

    private StatusEvaluation evaluateStatus(
            LocalDateTime lastHeartbeat,
            Integer bufferLevel,
            List<String> abnormalFlags,
            LocalDateTime evaluatedAt
    ) {
        if (lastHeartbeat == null) {
            return new StatusEvaluation(STATUS_OFFLINE, "NO_HEARTBEAT");
        }
        if (lastHeartbeat.isBefore(evaluatedAt.minusSeconds(OFFLINE_THRESHOLD_SECONDS))) {
            return new StatusEvaluation(STATUS_OFFLINE, "HEARTBEAT_TIMEOUT");
        }
        boolean highBuffer = bufferLevel != null && bufferLevel >= WARNING_BUFFER_LEVEL;
        boolean abnormal = abnormalFlags != null && !abnormalFlags.isEmpty();
        if (highBuffer && abnormal) {
            return new StatusEvaluation(STATUS_WARNING, "BUFFER_LEVEL_HIGH_AND_ABNORMAL");
        }
        if (highBuffer) {
            return new StatusEvaluation(STATUS_WARNING, "BUFFER_LEVEL_HIGH");
        }
        if (abnormal) {
            return new StatusEvaluation(STATUS_WARNING, "ABNORMAL_FLAGS_PRESENT");
        }
        return new StatusEvaluation(STATUS_ONLINE, "HEARTBEAT_OK");
    }

    private void validateDeviceRelation(String facilityId, String segmentId, String nodeId) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> invalidRelation("Facility not found: " + facilityId));
        SegmentEntity segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> invalidRelation("Segment not found: " + segmentId));
        NodeEntity node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> invalidRelation("Node not found: " + nodeId));

        if (!normalize(facility.getSegmentId()).equals(normalize(segment.getSegmentId()))) {
            throw invalidRelation("facilityId and segmentId do not match");
        }
        if (!normalize(facility.getNodeId()).equals(normalize(node.getNodeId()))) {
            throw invalidRelation("facilityId and nodeId do not match");
        }
        if (!normalize(segment.getStartNodeId()).equals(normalize(node.getNodeId()))
                && !normalize(segment.getEndNodeId()).equals(normalize(node.getNodeId()))) {
            throw invalidRelation("segmentId and nodeId do not form a valid object chain");
        }
    }

    private void syncDeviceBinding(DeviceEntity device) {
        ObjectScopeBindingEntity sourceBinding = objectScopeBindingRepository
                .findByObjectTypeAndObjectId(ObjectScopeService.OBJECT_FACILITY, device.getFacilityId())
                .or(() -> objectScopeBindingRepository.findByObjectTypeAndObjectId(ObjectScopeService.OBJECT_SEGMENT, device.getSegmentId()))
                .orElseThrow(() -> invalidRelation("Object scope binding is missing for facility: " + device.getFacilityId()));

        LocalDateTime now = LocalDateTime.now();
        ObjectScopeBindingEntity binding = objectScopeBindingRepository
                .findByObjectTypeAndObjectId(ObjectScopeService.OBJECT_DEVICE, device.getDeviceId())
                .orElseGet(() -> new ObjectScopeBindingEntity(
                        "OSB-DEV-" + UUID.randomUUID(),
                        ObjectScopeService.OBJECT_DEVICE,
                        device.getDeviceId(),
                        null,
                        null,
                        null,
                        ObjectScopeService.LEVEL_DETAIL,
                        now,
                        now
                ));
        binding.setObjectType(ObjectScopeService.OBJECT_DEVICE);
        binding.setObjectId(device.getDeviceId());
        binding.setRegionId(sourceBinding.getRegionId());
        binding.setOwnerUserId(sourceBinding.getOwnerUserId());
        binding.setOwnerUsername(sourceBinding.getOwnerUsername());
        binding.setScopeLevel(sourceBinding.getScopeLevel());
        binding.setUpdatedAt(now);
        objectScopeBindingRepository.save(binding);
    }

    private void syncDeviceRelation(DeviceEntity device) {
        objectRelationRepository.deleteByChildObjectTypeAndChildObjectIdAndRelationTypeIn(
                ObjectScopeService.OBJECT_DEVICE,
                device.getDeviceId(),
                List.of(ObjectChainTraversalService.RELATION_FACILITY_DEVICE)
        );
        LocalDateTime now = LocalDateTime.now();
        objectRelationRepository.save(new ObjectRelationEntity(
                "OR-FD-" + UUID.randomUUID(),
                ObjectScopeService.OBJECT_FACILITY,
                device.getFacilityId(),
                ObjectScopeService.OBJECT_DEVICE,
                device.getDeviceId(),
                ObjectChainTraversalService.RELATION_FACILITY_DEVICE,
                true,
                now,
                now
        ));
    }

    private DeviceLedgerResponse toResponse(DeviceEntity device, ObjectScopeBindingEntity binding) {
        List<String> abnormalFlags = decodeFlags(device.getLastAbnormalFlags());
        return new DeviceLedgerResponse(
                device.getDeviceId(),
                device.getDeviceName(),
                device.getFacilityId(),
                device.getSegmentId(),
                device.getNodeId(),
                binding == null ? null : binding.getRegionId(),
                device.getProtocolType(),
                normalize(device.getStatus()),
                normalize(device.getOnlineStatusReason()),
                device.getLastHeartbeat(),
                device.getLastRecvTime(),
                device.getLastBufferLevel(),
                abnormalFlags,
                device.getCalibrationDueAt(),
                isCalibrationExpired(device.getCalibrationDueAt()),
                device.getVersionNo()
        );
    }

    private boolean isCalibrationExpired(LocalDateTime calibrationDueAt) {
        return calibrationDueAt != null && calibrationDueAt.isBefore(LocalDateTime.now());
    }

    private Map<String, ObjectScopeBindingEntity> loadBindings(Collection<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }
        return objectScopeBindingRepository.findByObjectTypeAndObjectIdIn(ObjectScopeService.OBJECT_DEVICE, deviceIds).stream()
                .collect(Collectors.toMap(ObjectScopeBindingEntity::getObjectId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private boolean matchesFilter(String value, String filter) {
        return filter == null || filter.isBlank() || normalize(value).equals(normalize(filter));
    }

    private List<String> normalizeFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String flag : flags) {
            String candidate = normalize(flag);
            if (!candidate.isEmpty()) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> decodeFlags(String encodedFlags) {
        if (encodedFlags == null || encodedFlags.isBlank()) {
            return List.of();
        }
        List<String> flags = new ArrayList<>();
        for (String raw : encodedFlags.split(",")) {
            String candidate = normalize(raw);
            if (!candidate.isEmpty()) {
                flags.add(candidate);
            }
        }
        return List.copyOf(flags);
    }

    private String encodeFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return null;
        }
        return String.join(",", normalizeFlags(flags));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private AuthFlowException invalidRelation(String message) {
        return new AuthFlowException(ErrorCode.DEVICE_RELATION_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    private AuthFlowException invalidHeartbeat(String message) {
        return new AuthFlowException(ErrorCode.DEVICE_HEARTBEAT_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    public record UpsertResult(DeviceLedgerResponse response, boolean created) {
    }

    private record StatusEvaluation(String status, String reason) {
    }
}
