package com.uscdip.backend.service;

import com.uscdip.backend.dto.CalibrationCorrectedPreviewResponse;
import com.uscdip.backend.dto.CalibrationDriftCheckRequest;
import com.uscdip.backend.dto.CalibrationDriftRecordResponse;
import com.uscdip.backend.dto.CalibrationProfileCreateRequest;
import com.uscdip.backend.dto.CalibrationProfileResponse;
import com.uscdip.backend.entity.CalibrationDriftRecordEntity;
import com.uscdip.backend.entity.CalibrationProfileEntity;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.entity.WorkOrderEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.repository.CalibrationDriftRecordRepository;
import com.uscdip.backend.repository.CalibrationProfileRepository;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.IncidentRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.TsMetricRepository;
import com.uscdip.backend.repository.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CalibrationManagementService {

    static final String STATUS_DRAFT = "DRAFT";
    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_EXPIRED = "EXPIRED";
    static final String STATUS_SUPERSEDED = "SUPERSEDED";
    static final String DRIFT_NORMAL = "NORMAL";
    static final String DRIFT_SUSPECTED = "SUSPECTED";
    static final String DRIFT_CONFIRMED = "CONFIRMED";
    private static final String INCIDENT_STATUS_OPEN = "OPEN";
    private static final String WORK_ORDER_STATUS_CREATED = "CREATED";
    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final String MENU_ASSET_WRITE = "MENU:ASSET:WRITE";
    private static final Set<String> CLOSED_WORK_ORDER_STATUSES = Set.of("CLOSED", "DONE", "CANCELLED", "RESOLVED");
    private static final Set<String> REMINDER_CANDIDATE_STATUSES = Set.of(STATUS_ACTIVE, STATUS_EXPIRED);

    private final CalibrationProfileRepository calibrationProfileRepository;
    private final CalibrationDriftRecordRepository calibrationDriftRecordRepository;
    private final DeviceRepository deviceRepository;
    private final TsMetricRepository tsMetricRepository;
    private final IncidentRepository incidentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final ObjectScopeService objectScopeService;
    private final IdempotentConsumerService idempotentConsumerService;

    public CalibrationManagementService(
            CalibrationProfileRepository calibrationProfileRepository,
            CalibrationDriftRecordRepository calibrationDriftRecordRepository,
            DeviceRepository deviceRepository,
            TsMetricRepository tsMetricRepository,
            IncidentRepository incidentRepository,
            WorkOrderRepository workOrderRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            ObjectScopeService objectScopeService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        this.calibrationProfileRepository = calibrationProfileRepository;
        this.calibrationDriftRecordRepository = calibrationDriftRecordRepository;
        this.deviceRepository = deviceRepository;
        this.tsMetricRepository = tsMetricRepository;
        this.incidentRepository = incidentRepository;
        this.workOrderRepository = workOrderRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.objectScopeService = objectScopeService;
        this.idempotentConsumerService = idempotentConsumerService;
    }

    @Transactional
    public CalibrationProfileResponse createProfile(
            AuthorizationContext context,
            String deviceId,
            CalibrationProfileCreateRequest request
    ) {
        DeviceEntity device = requireDevice(deviceId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, device.getDeviceId(), MENU_ASSET_WRITE);
        validateProfileRequest(request);

        String metricCode = normalize(request.metricCode());
        String profileVersion = normalize(request.profileVersion());
        if (calibrationProfileRepository.findFirstByDeviceIdAndMetricCodeAndProfileVersionOrderByCreatedAtDesc(
                device.getDeviceId(),
                metricCode,
                profileVersion
        ).isPresent()) {
            throw new AuthFlowException(
                    ErrorCode.CALIBRATION_PROFILE_CONFLICT,
                    HttpStatus.CONFLICT,
                    "Calibration profile already exists for device: " + deviceId + ", metricCode: " + metricCode + ", profileVersion: " + profileVersion
            );
        }

        LocalDateTime now = LocalDateTime.now();
        if (request.activate()) {
            supersedeExistingActiveProfiles(device.getDeviceId(), metricCode, now);
        }
        CalibrationProfileEntity profile = new CalibrationProfileEntity(
                "CALP-" + UUID.randomUUID(),
                device.getDeviceId(),
                profileVersion,
                metricCode,
                request.calibratedAt(),
                request.effectiveFrom(),
                request.effectiveUntil(),
                request.operatorName().trim(),
                blankToNull(request.referenceStandard()),
                scale(request.correctionSlope()),
                scale(request.correctionOffset()),
                scale(request.driftThresholdAbs()),
                scale(request.driftThresholdPct()),
                resolveProfileStatus(request.activate(), request.effectiveUntil()),
                now,
                now
        );
        calibrationProfileRepository.save(profile);

        if (request.activate()) {
            device.setCalibrationDueAt(request.effectiveUntil());
            device.setUpdatedAt(now);
            deviceRepository.save(device);
            ensureReminderWorkOrder(profile, device, now);
        }
        return toProfileResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<CalibrationProfileResponse> listProfiles(AuthorizationContext context, String deviceId) {
        DeviceEntity device = requireDevice(deviceId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, device.getDeviceId(), MENU_ASSET_READ);
        return calibrationProfileRepository.findByDeviceIdOrderByCalibratedAtDescCreatedAtDesc(device.getDeviceId()).stream()
                .map(this::toProfileResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CalibrationProfileResponse> getActiveProfile(AuthorizationContext context, String deviceId, String metricCode) {
        DeviceEntity device = requireDevice(deviceId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, device.getDeviceId(), MENU_ASSET_READ);
        return calibrationProfileRepository.findFirstByDeviceIdAndMetricCodeAndStatusOrderByEffectiveFromDescCreatedAtDesc(
                        device.getDeviceId(),
                        normalize(metricCode),
                        STATUS_ACTIVE
                )
                .map(this::toProfileResponse);
    }

    @Transactional
    public CalibrationDriftRecordResponse createDriftCheck(
            AuthorizationContext context,
            String deviceId,
            CalibrationDriftCheckRequest request
    ) {
        DeviceEntity device = requireDevice(deviceId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, device.getDeviceId(), MENU_ASSET_WRITE);
        validateDriftRequest(request);

        String metricCode = normalize(request.metricCode());
        String profileVersion = normalize(request.profileVersion());
        CalibrationProfileEntity profile = calibrationProfileRepository.findFirstByDeviceIdAndMetricCodeAndProfileVersionOrderByCreatedAtDesc(
                        device.getDeviceId(),
                        metricCode,
                        profileVersion
                )
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.CALIBRATION_PROFILE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Calibration profile not found for device: " + deviceId + ", metricCode: " + metricCode + ", profileVersion: " + profileVersion
                ));

        BigDecimal deviationAbs = request.observedValue().subtract(request.referenceValue()).abs();
        BigDecimal deviationPct = computeDeviationPct(request.observedValue(), request.referenceValue());
        String driftStatus = evaluateDriftStatus(deviationAbs, deviationPct, profile);
        LocalDateTime now = LocalDateTime.now();
        IncidentWorkOrderPair pair = DRIFT_CONFIRMED.equals(driftStatus)
                ? createCalibrationIssue(device, profile, "DRIFT_CONFIRMED", now)
                : IncidentWorkOrderPair.empty();

        CalibrationDriftRecordEntity record = new CalibrationDriftRecordEntity(
                "CALD-" + UUID.randomUUID(),
                profile.getProfileId(),
                device.getDeviceId(),
                metricCode,
                scale(request.observedValue()),
                scale(request.referenceValue()),
                scale(deviationAbs),
                scale(deviationPct),
                request.checkedAt(),
                request.checkedBy().trim(),
                driftStatus,
                pair.workOrderId(),
                pair.incidentId(),
                now
        );
        calibrationDriftRecordRepository.save(record);
        return toDriftResponse(record, profile.getProfileVersion());
    }

    @Transactional(readOnly = true)
    public List<CalibrationDriftRecordResponse> listDriftChecks(AuthorizationContext context, String deviceId) {
        DeviceEntity device = requireDevice(deviceId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, device.getDeviceId(), MENU_ASSET_READ);
        Map<String, String> profileVersions = calibrationProfileRepository.findByDeviceIdOrderByCalibratedAtDescCreatedAtDesc(device.getDeviceId()).stream()
                .collect(Collectors.toMap(CalibrationProfileEntity::getProfileId, CalibrationProfileEntity::getProfileVersion, (left, right) -> left, LinkedHashMap::new));
        return calibrationDriftRecordRepository.findByDeviceIdOrderByCheckedAtDescCreatedAtDesc(device.getDeviceId()).stream()
                .map(record -> toDriftResponse(record, profileVersions.get(record.getProfileId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CalibrationCorrectedPreviewResponse getCorrectedPreview(
            AuthorizationContext context,
            String sourceRecordId,
            String profileVersion
    ) {
        TsMetricEntity metric = tsMetricRepository.findById(sourceRecordId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.CALIBRATION_CORRECTION_PREVIEW_INVALID,
                        HttpStatus.NOT_FOUND,
                        "TS metric not found for sourceRecordId: " + sourceRecordId
                ));
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, metric.getDeviceId(), MENU_ASSET_READ);

        CalibrationProfileEntity profile = resolvePreviewProfile(metric, profileVersion);
        BigDecimal rawValue;
        try {
            rawValue = new BigDecimal(metric.getMetricValue());
        } catch (NumberFormatException ex) {
            throw new AuthFlowException(
                    ErrorCode.CALIBRATION_CORRECTION_PREVIEW_INVALID,
                    HttpStatus.BAD_REQUEST,
                    "Metric value is not numeric for sourceRecordId: " + sourceRecordId
            );
        }
        BigDecimal correctedValue = rawValue.multiply(profile.getCorrectionSlope()).add(profile.getCorrectionOffset()).setScale(6, RoundingMode.HALF_UP);
        return new CalibrationCorrectedPreviewResponse(
                metric.getSourceRecordId(),
                metric.getDeviceId(),
                metric.getMetricCode(),
                rawValue,
                profile.getProfileVersion(),
                profile.getCorrectionSlope(),
                profile.getCorrectionOffset(),
                correctedValue,
                metric.getDqScore(),
                isCalibrationExpired(profile.getEffectiveUntil())
        );
    }

    @Transactional
    public int runDueReminderScan() {
        return scanDueProfiles();
    }

    @Scheduled(cron = "${backend.calibration.reminder-scan-cron:0 0 2 * * *}")
    @Transactional
    public int scanDueProfiles() {
        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        List<CalibrationProfileEntity> candidates = calibrationProfileRepository.findByStatusInOrderByEffectiveUntilAscCreatedAtAsc(
                REMINDER_CANDIDATE_STATUSES
        );
        for (CalibrationProfileEntity profile : candidates) {
            if (!isReminderCandidate(profile, now)) {
                continue;
            }
            DeviceEntity device = deviceRepository.findById(profile.getDeviceId()).orElse(null);
            if (device == null) {
                continue;
            }
            if (ensureReminderWorkOrder(profile, device, now)) {
                created++;
            }
        }
        return created;
    }

    private CalibrationProfileEntity resolvePreviewProfile(TsMetricEntity metric, String profileVersion) {
        String metricCode = normalize(metric.getMetricCode());
        if (profileVersion != null && !profileVersion.isBlank()) {
            return calibrationProfileRepository.findFirstByDeviceIdAndMetricCodeAndProfileVersionOrderByCreatedAtDesc(
                            metric.getDeviceId(),
                            metricCode,
                            normalize(profileVersion)
                    )
                    .orElseThrow(() -> new AuthFlowException(
                            ErrorCode.CALIBRATION_PROFILE_NOT_FOUND,
                            HttpStatus.NOT_FOUND,
                            "Calibration profile not found for device: " + metric.getDeviceId() + ", metricCode: " + metricCode + ", profileVersion: " + profileVersion
                    ));
        }
        return calibrationProfileRepository.findFirstByDeviceIdAndMetricCodeAndStatusOrderByEffectiveFromDescCreatedAtDesc(
                        metric.getDeviceId(),
                        metricCode,
                        STATUS_ACTIVE
                )
                .or(() -> calibrationProfileRepository.findByDeviceIdAndMetricCodeOrderByCalibratedAtDescCreatedAtDesc(metric.getDeviceId(), metricCode).stream()
                        .filter(profile -> !STATUS_DRAFT.equals(profile.getStatus()))
                        .findFirst())
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.CALIBRATION_PROFILE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Calibration profile not found for device: " + metric.getDeviceId() + ", metricCode: " + metricCode
                ));
    }

    private boolean isReminderCandidate(CalibrationProfileEntity profile, LocalDateTime now) {
        if (profile.getEffectiveUntil() == null || STATUS_SUPERSEDED.equals(profile.getStatus()) || STATUS_DRAFT.equals(profile.getStatus())) {
            return false;
        }
        long daysUntilDue = ChronoUnit.DAYS.between(now.toLocalDate().atStartOfDay(), profile.getEffectiveUntil().toLocalDate().atStartOfDay());
        return daysUntilDue <= 7;
    }

    private void validateProfileRequest(CalibrationProfileCreateRequest request) {
        if (request.effectiveUntil().isBefore(request.effectiveFrom())) {
            throw new AuthFlowException(
                    ErrorCode.CALIBRATION_PROFILE_CONFLICT,
                    HttpStatus.BAD_REQUEST,
                    "effectiveUntil must be greater than or equal to effectiveFrom"
            );
        }
    }

    private void validateDriftRequest(CalibrationDriftCheckRequest request) {
        if (request.checkedAt() == null) {
            throw new AuthFlowException(
                    ErrorCode.CALIBRATION_DRIFT_EVALUATION_INVALID,
                    HttpStatus.BAD_REQUEST,
                    "checkedAt is required"
            );
        }
    }

    private void supersedeExistingActiveProfiles(String deviceId, String metricCode, LocalDateTime now) {
        List<CalibrationProfileEntity> profiles = calibrationProfileRepository.findByDeviceIdAndMetricCodeOrderByCalibratedAtDescCreatedAtDesc(deviceId, metricCode);
        List<CalibrationProfileEntity> activeProfiles = profiles.stream()
                .filter(profile -> STATUS_ACTIVE.equals(profile.getStatus()))
                .toList();
        if (activeProfiles.isEmpty()) {
            return;
        }
        for (CalibrationProfileEntity profile : activeProfiles) {
            profile.setStatus(STATUS_SUPERSEDED);
            profile.setUpdatedAt(now);
        }
        calibrationProfileRepository.saveAll(activeProfiles);
    }

    private String resolveProfileStatus(boolean activate, LocalDateTime effectiveUntil) {
        if (!activate) {
            return STATUS_DRAFT;
        }
        return isCalibrationExpired(effectiveUntil) ? STATUS_EXPIRED : STATUS_ACTIVE;
    }

    private String evaluateDriftStatus(
            BigDecimal deviationAbs,
            BigDecimal deviationPct,
            CalibrationProfileEntity profile
    ) {
        boolean confirmed = exceedsThreshold(deviationAbs, profile.getDriftThresholdAbs())
                || exceedsThreshold(deviationPct, profile.getDriftThresholdPct());
        if (confirmed) {
            return DRIFT_CONFIRMED;
        }
        boolean suspected = exceedsThreshold(deviationAbs, profile.getDriftThresholdAbs().multiply(BigDecimal.valueOf(0.5)))
                || exceedsThreshold(deviationPct, profile.getDriftThresholdPct().multiply(BigDecimal.valueOf(0.5)));
        return suspected ? DRIFT_SUSPECTED : DRIFT_NORMAL;
    }

    private boolean exceedsThreshold(BigDecimal actual, BigDecimal threshold) {
        return threshold != null && actual != null && actual.compareTo(threshold) >= 0;
    }

    private BigDecimal computeDeviationPct(BigDecimal observedValue, BigDecimal referenceValue) {
        if (referenceValue == null || BigDecimal.ZERO.compareTo(referenceValue.abs()) == 0) {
            return observedValue.subtract(referenceValue == null ? BigDecimal.ZERO : referenceValue).abs().compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100).setScale(6, RoundingMode.HALF_UP);
        }
        return observedValue.subtract(referenceValue)
                .abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(referenceValue.abs(), 6, RoundingMode.HALF_UP);
    }

    private IncidentWorkOrderPair createCalibrationIssue(
            DeviceEntity device,
            CalibrationProfileEntity profile,
            String reasonCode,
            LocalDateTime now
    ) {
        String normalizedReason = normalize(reasonCode);
        String title = switch (normalizedReason) {
            case "DRIFT_CONFIRMED" -> "Calibration drift confirmed " + device.getDeviceId() + " " + profile.getMetricCode() + " " + profile.getProfileVersion();
            case "EXPIRED" -> "Calibration expired " + device.getDeviceId() + " " + profile.getMetricCode() + " " + profile.getProfileVersion();
            default -> "Calibration expiring " + device.getDeviceId() + " " + profile.getMetricCode() + " " + profile.getProfileVersion();
        };
        String severity = "DRIFT_CONFIRMED".equals(normalizedReason) || "EXPIRED".equals(normalizedReason) ? "HIGH" : "MEDIUM";
        ObjectScopeBindingEntity deviceBinding = requireDeviceBinding(device.getDeviceId());

        String incidentId = buildCalibrationIncidentId(device, profile, normalizedReason);
        IncidentEntity incident = incidentRepository.findById(incidentId).orElseGet(() -> new IncidentEntity(
                incidentId,
                device.getSegmentId(),
                device.getNodeId(),
                device.getDeviceId(),
                IncidentEventizationService.INCIDENT_TYPE_CALIBRATION_GOVERNANCE,
                title,
                severity,
                severity,
                INCIDENT_STATUS_OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                1L
        ));
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        syncScopedObject(ObjectScopeService.OBJECT_INCIDENT, incident.getIncidentId(), deviceBinding, now);

        String workOrderId = createWorkOrderOnce(incident, device, deviceBinding, normalizedReason, now);

        return new IncidentWorkOrderPair(incident.getIncidentId(), workOrderId);
    }

    private String createWorkOrderOnce(
            IncidentEntity incident,
            DeviceEntity device,
            ObjectScopeBindingEntity deviceBinding,
            String reasonCode,
            LocalDateTime now
    ) {
        IdempotentConsumerResult result = idempotentConsumerService.consume(
                IdempotentConsumerService.ACTION_CREATE_WORK_ORDER,
                ObjectScopeService.OBJECT_INCIDENT,
                incident.getIncidentId(),
                null,
                incident.getVersionNo(),
                buildWorkOrderPayload(incident, device, reasonCode),
                incident.getTraceId(),
                () -> {
                    WorkOrderEntity workOrder = new WorkOrderEntity();
                    workOrder.setWorkOrderId("WO-CAL-" + UUID.randomUUID());
                    workOrder.setIncidentId(incident.getIncidentId());
                    workOrder.setSegmentId(device.getSegmentId());
                    workOrder.setNodeId(device.getNodeId());
                    workOrder.setWorkOrderType("CALIBRATION_GOVERNANCE");
                    workOrder.setPriority("HIGH");
                    workOrder.setDescription(incident.getTitle());
                    workOrder.setAssigneeUserId(blankToNull(deviceBinding.getOwnerUserId()));
                    workOrder.setAssignee(blankToNull(deviceBinding.getOwnerUsername()));
                    workOrder.setStatus(WORK_ORDER_STATUS_CREATED);
                    workOrder.setCreatedBy("SYSTEM");
                    workOrder.setCreatedAt(now);
                    workOrder.setUpdatedAt(now);
                    workOrder.setVersionNo(1L);
                    workOrderRepository.save(workOrder);
                    syncScopedObject(ObjectScopeService.OBJECT_WORK_ORDER, workOrder.getWorkOrderId(), deviceBinding, now);
                    return workOrder.getWorkOrderId();
                }
        );
        return result.resultRefId();
    }

    private String buildCalibrationIncidentId(DeviceEntity device, CalibrationProfileEntity profile, String reasonCode) {
        String source = normalize(device.getDeviceId()) + "|" + normalize(profile.getProfileId()) + "|" + normalize(reasonCode);
        return "INC-CAL-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private String buildWorkOrderPayload(IncidentEntity incident, DeviceEntity device, String reasonCode) {
        return "{"
                + "\"incidentId\":\"" + blankToEmpty(incident.getIncidentId()) + "\","
                + "\"deviceId\":\"" + blankToEmpty(device.getDeviceId()) + "\","
                + "\"actionType\":\"" + IdempotentConsumerService.ACTION_CREATE_WORK_ORDER + "\","
                + "\"reasonCode\":\"" + blankToEmpty(reasonCode) + "\","
                + "\"versionNo\":" + (incident.getVersionNo() == null ? 0 : incident.getVersionNo())
                + "}";
    }

    private boolean ensureReminderWorkOrder(CalibrationProfileEntity profile, DeviceEntity device, LocalDateTime now) {
        if (!isReminderCandidate(profile, now)) {
            return false;
        }
        String reasonCode = isCalibrationExpired(profile.getEffectiveUntil()) ? "EXPIRED" : "EXPIRING";
        String expectedTitle = "EXPIRED".equals(reasonCode)
                ? "Calibration expired " + device.getDeviceId() + " " + profile.getMetricCode() + " " + profile.getProfileVersion()
                : "Calibration expiring " + device.getDeviceId() + " " + profile.getMetricCode() + " " + profile.getProfileVersion();

        List<IncidentEntity> matchedIncidents = incidentRepository.findBySegmentIdAndNodeId(device.getSegmentId(), device.getNodeId()).stream()
                .filter(incident -> expectedTitle.equalsIgnoreCase(blankToEmpty(incident.getTitle())))
                .filter(incident -> !isClosedStatus(incident.getStatus()))
                .toList();
        if (!matchedIncidents.isEmpty()) {
            List<String> incidentIds = matchedIncidents.stream().map(IncidentEntity::getIncidentId).toList();
            boolean hasOpenWorkOrder = workOrderRepository.findByIncidentIdIn(incidentIds).stream()
                    .anyMatch(workOrder -> !isClosedStatus(workOrder.getStatus()));
            if (hasOpenWorkOrder) {
                return false;
            }
        }

        createCalibrationIssue(device, profile, reasonCode, now);
        return true;
    }

    private boolean isClosedStatus(String status) {
        return status != null && CLOSED_WORK_ORDER_STATUSES.contains(normalize(status));
    }

    private void syncScopedObject(
            String objectType,
            String objectId,
            ObjectScopeBindingEntity sourceBinding,
            LocalDateTime now
    ) {
        ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId(objectType, objectId)
                .orElseGet(() -> new ObjectScopeBindingEntity(
                        "OSB-" + normalize(objectType) + "-" + UUID.randomUUID(),
                        objectType,
                        objectId,
                        null,
                        null,
                        null,
                        ObjectScopeService.LEVEL_DETAIL,
                        now,
                        now
                ));
        binding.setObjectType(objectType);
        binding.setObjectId(objectId);
        binding.setRegionId(sourceBinding.getRegionId());
        binding.setOwnerUserId(sourceBinding.getOwnerUserId());
        binding.setOwnerUsername(sourceBinding.getOwnerUsername());
        binding.setScopeLevel(sourceBinding.getScopeLevel());
        binding.setUpdatedAt(now);
        objectScopeBindingRepository.save(binding);
    }

    private ObjectScopeBindingEntity requireDeviceBinding(String deviceId) {
        return objectScopeBindingRepository.findByObjectTypeAndObjectId(ObjectScopeService.OBJECT_DEVICE, deviceId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.DEVICE_NOT_FOUND,
                        HttpStatus.BAD_REQUEST,
                        "Object scope binding is missing for device: " + deviceId
                ));
    }

    private DeviceEntity requireDevice(String deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.DEVICE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Device not found: " + deviceId
                ));
    }

    private CalibrationProfileResponse toProfileResponse(CalibrationProfileEntity profile) {
        return new CalibrationProfileResponse(
                profile.getProfileId(),
                profile.getDeviceId(),
                profile.getProfileVersion(),
                profile.getMetricCode(),
                profile.getCalibratedAt(),
                profile.getEffectiveFrom(),
                profile.getEffectiveUntil(),
                profile.getOperatorName(),
                profile.getReferenceStandard(),
                profile.getCorrectionSlope(),
                profile.getCorrectionOffset(),
                profile.getDriftThresholdAbs(),
                profile.getDriftThresholdPct(),
                profile.getStatus(),
                isCalibrationExpired(profile.getEffectiveUntil()),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private CalibrationDriftRecordResponse toDriftResponse(CalibrationDriftRecordEntity record, String profileVersion) {
        return new CalibrationDriftRecordResponse(
                record.getDriftRecordId(),
                record.getProfileId(),
                record.getDeviceId(),
                profileVersion,
                record.getMetricCode(),
                record.getObservedValue(),
                record.getReferenceValue(),
                record.getDeviationAbs(),
                record.getDeviationPct(),
                record.getCheckedAt(),
                record.getCheckedBy(),
                record.getDriftStatus(),
                record.getIncidentId(),
                record.getWorkOrderId(),
                record.getCreatedAt()
        );
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    private boolean isCalibrationExpired(LocalDateTime effectiveUntil) {
        return effectiveUntil != null && effectiveUntil.isBefore(LocalDateTime.now());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record IncidentWorkOrderPair(String incidentId, String workOrderId) {
        private static IncidentWorkOrderPair empty() {
            return new IncidentWorkOrderPair(null, null);
        }
    }
}
