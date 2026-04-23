package com.uscdip.backend.service;

import com.uscdip.backend.dto.IncidentResponse;
import com.uscdip.backend.entity.AlertCaseEntity;
import com.uscdip.backend.entity.AlertRecordEntity;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.IncidentRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncidentEventizationService {

    static final String INCIDENT_TYPE_ALERT_EVENT = "ALERT_EVENT";
    static final String INCIDENT_TYPE_CALIBRATION_GOVERNANCE = "CALIBRATION_GOVERNANCE";
    static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    static final String STATUS_OPEN = "OPEN";
    static final String STATUS_RESOLVED = "RESOLVED";
    static final String STATUS_CLOSED = "CLOSED";
    static final String STATUS_FALSE_POSITIVE = "FALSE_POSITIVE";

    private static final String CASE_STATUS_ACTIVE = "ACTIVE";
    private static final String CASE_STATUS_ESCALATED = "ESCALATED";
    private static final String CASE_STATUS_SUPPRESSED = "SUPPRESSED";
    private static final String CASE_STATUS_REVIEW_ONLY = "REVIEW_ONLY";
    private static final String CASE_STATUS_DQ_BLOCKED = "DQ_BLOCKED";
    private static final String CASE_STATUS_RECOVERED = "RECOVERED";
    private static final String DECISION_TRIGGERED = "TRIGGERED";
    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final String MENU_ASSET_WRITE = "MENU:ASSET:WRITE";
    private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_RESOLVED, STATUS_CLOSED, STATUS_FALSE_POSITIVE);

    private final IncidentRepository incidentRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final ObjectScopeService objectScopeService;
    private final OutboxService outboxService;

    public IncidentEventizationService(
            IncidentRepository incidentRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            ObjectScopeService objectScopeService,
            OutboxService outboxService
    ) {
        this.incidentRepository = incidentRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.objectScopeService = objectScopeService;
        this.outboxService = outboxService;
    }

    @Transactional
    public void syncIncidentForCase(AlertCaseEntity caseEntity, AlertRecordEntity latestRecord) {
        if (caseEntity == null) {
            return;
        }

        String caseStatus = normalize(caseEntity.getCaseStatus());
        if (CASE_STATUS_RECOVERED.equals(caseStatus)) {
            resolveIncidentForRecoveredCase(caseEntity);
            return;
        }
        if (CASE_STATUS_DQ_BLOCKED.equals(caseStatus) || latestRecord == null) {
            return;
        }

        String targetStatus = resolveIncidentStatus(caseStatus, caseEntity.getDecisionSnapshot());
        if (targetStatus == null) {
            return;
        }

        IncidentEntity incident = incidentRepository.findBySourceCaseId(caseEntity.getCaseId()).orElseGet(IncidentEntity::new);
        boolean isNew = incident.getIncidentId() == null;
        LocalDateTime now = LocalDateTime.now();

        if (isNew) {
            incident.setIncidentId("INC-ALERT-" + UUID.randomUUID());
            incident.setCreatedAt(now);
            incident.setIncidentType(INCIDENT_TYPE_ALERT_EVENT);
            incident.setVersionNo(1L);
        } else {
            incident.setVersionNo(safeVersion(incident.getVersionNo()) + 1L);
        }

        incident.setSegmentId(caseEntity.getSegmentId());
        incident.setNodeId(caseEntity.getNodeId());
        incident.setDeviceId(caseEntity.getDeviceId());
        incident.setTitle(buildTitle(caseEntity, latestRecord));
        incident.setSeverity(normalize(caseEntity.getSeverityCurrent()));
        incident.setSeveritySource(normalize(latestRecord.getSeverity()));
        incident.setStatus(targetStatus);
        incident.setSourceCaseId(caseEntity.getCaseId());
        incident.setSourceAlertId(latestRecord.getAlertId());
        incident.setSourceRuleCode(normalize(caseEntity.getRuleCode()));
        incident.setSourceBatchId(latestRecord.getSourceBatchId());
        incident.setDqScoreSnapshot(latestRecord.getDqScoreSnapshot());
        incident.setAlertConfFinal(latestRecord.getAlertConfFinal());
        incident.setTraceId(latestRecord.getTraceId());
        incident.setUpdatedAt(now);
        if (STATUS_OPEN.equals(targetStatus)) {
            incident.setResolvedAt(null);
            incident.setCloseReason(null);
        }

        incidentRepository.save(incident);
        syncIncidentScope(incident, now);
        outboxService.publishIncidentChanged(incident, isNew, now);
    }

    @Transactional
    public void resolveIncidentForRecoveredCase(AlertCaseEntity caseEntity) {
        if (caseEntity == null || caseEntity.getCaseId() == null) {
            return;
        }
        IncidentEntity incident = incidentRepository.findBySourceCaseId(caseEntity.getCaseId()).orElse(null);
        if (incident == null || TERMINAL_STATUSES.contains(normalize(incident.getStatus()))) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        incident.setStatus(STATUS_RESOLVED);
        incident.setResolvedAt(now);
        incident.setCloseReason("AUTO_RECOVERED");
        incident.setUpdatedAt(now);
        incident.setVersionNo(safeVersion(incident.getVersionNo()) + 1L);
        incidentRepository.save(incident);
        outboxService.publishIncidentResolved(incident, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> listIncidents(
            AuthorizationContext context,
            int page,
            int pageSize,
            String deviceId,
            String ruleCode,
            String status,
            String incidentType
    ) {
        List<IncidentResponse> items = filterVisibleIncidents(context, incidentRepository.findAllByOrderByUpdatedAtDescIncidentIdDesc()).stream()
                .filter(incident -> matches(incident.getDeviceId(), deviceId))
                .filter(incident -> matches(incident.getSourceRuleCode(), ruleCode))
                .filter(incident -> matches(incident.getStatus(), status))
                .filter(incident -> matches(incident.getIncidentType(), incidentType))
                .map(this::toResponse)
                .toList();
        return paginate(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncidentDetail(AuthorizationContext context, String incidentId) {
        IncidentEntity incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.INCIDENT_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Incident not found: " + incidentId
                ));
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_INCIDENT, incidentId, MENU_ASSET_READ);
        return toResponse(incident);
    }

    @Transactional
    public IncidentResponse confirmIncident(AuthorizationContext context, String incidentId) {
        IncidentEntity incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.INCIDENT_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Incident not found: " + incidentId
                ));
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_INCIDENT, incidentId, MENU_ASSET_WRITE);
        if (!STATUS_PENDING_CONFIRMATION.equals(normalize(incident.getStatus()))) {
            throw new AuthFlowException(
                    ErrorCode.INCIDENT_INVALID_STATE,
                    HttpStatus.CONFLICT,
                    "Only PENDING_CONFIRMATION incidents can be confirmed"
            );
        }
        LocalDateTime now = LocalDateTime.now();
        incident.setStatus(STATUS_OPEN);
        incident.setConfirmedBy(context.userId());
        incident.setConfirmedAt(now);
        incident.setResolvedAt(null);
        incident.setCloseReason(null);
        incident.setUpdatedAt(now);
        incident.setVersionNo(safeVersion(incident.getVersionNo()) + 1L);
        incidentRepository.save(incident);
        outboxService.publishIncidentConfirmed(incident, now);
        return toResponse(incident);
    }

    private String resolveIncidentStatus(String caseStatus, String decisionSnapshot) {
        String normalizedCaseStatus = normalize(caseStatus);
        if (CASE_STATUS_REVIEW_ONLY.equals(normalizedCaseStatus)) {
            return STATUS_PENDING_CONFIRMATION;
        }
        if ((CASE_STATUS_ACTIVE.equals(normalizedCaseStatus)
                || CASE_STATUS_ESCALATED.equals(normalizedCaseStatus)
                || CASE_STATUS_SUPPRESSED.equals(normalizedCaseStatus))
                && DECISION_TRIGGERED.equals(normalize(decisionSnapshot))) {
            return STATUS_OPEN;
        }
        return null;
    }

    private void syncIncidentScope(IncidentEntity incident, LocalDateTime now) {
        if (incident == null || incident.getIncidentId() == null || incident.getDeviceId() == null || incident.getDeviceId().isBlank()) {
            return;
        }
        ObjectScopeBindingEntity deviceBinding = objectScopeBindingRepository.findByObjectTypeAndObjectId(
                        ObjectScopeService.OBJECT_DEVICE,
                        incident.getDeviceId()
                )
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.DEVICE_NOT_FOUND,
                        HttpStatus.BAD_REQUEST,
                        "Object scope binding is missing for device: " + incident.getDeviceId()
                ));
        ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId(
                        ObjectScopeService.OBJECT_INCIDENT,
                        incident.getIncidentId()
                )
                .orElseGet(() -> new ObjectScopeBindingEntity(
                        "OSB-INCIDENT-" + UUID.randomUUID(),
                        ObjectScopeService.OBJECT_INCIDENT,
                        incident.getIncidentId(),
                        null,
                        null,
                        null,
                        ObjectScopeService.LEVEL_DETAIL,
                        now,
                        now
                ));
        binding.setObjectType(ObjectScopeService.OBJECT_INCIDENT);
        binding.setObjectId(incident.getIncidentId());
        binding.setRegionId(deviceBinding.getRegionId());
        binding.setOwnerUserId(deviceBinding.getOwnerUserId());
        binding.setOwnerUsername(deviceBinding.getOwnerUsername());
        binding.setScopeLevel(deviceBinding.getScopeLevel());
        binding.setUpdatedAt(now);
        objectScopeBindingRepository.save(binding);
    }

    private List<IncidentEntity> filterVisibleIncidents(AuthorizationContext context, List<IncidentEntity> incidents) {
        if (incidents.isEmpty()) {
            return List.of();
        }
        Set<String> accessibleIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_INCIDENT,
                incidents.stream().map(IncidentEntity::getIncidentId).collect(Collectors.toCollection(LinkedHashSet::new)),
                MENU_ASSET_READ
        );
        return incidents.stream()
                .filter(incident -> accessibleIds.contains(incident.getIncidentId()))
                .toList();
    }

    private PageResponse<IncidentResponse> paginate(List<IncidentResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private IncidentResponse toResponse(IncidentEntity entity) {
        return new IncidentResponse(
                entity.getIncidentId(),
                entity.getIncidentType(),
                entity.getSegmentId(),
                entity.getNodeId(),
                entity.getDeviceId(),
                entity.getTitle(),
                entity.getSeverity(),
                entity.getSeveritySource(),
                entity.getStatus(),
                entity.getSourceCaseId(),
                entity.getSourceAlertId(),
                entity.getSourceRuleCode(),
                entity.getSourceBatchId(),
                entity.getDqScoreSnapshot(),
                entity.getAlertConfFinal(),
                entity.getConfirmedBy(),
                entity.getConfirmedAt(),
                entity.getResolvedAt(),
                entity.getCloseReason(),
                entity.getTraceId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersionNo()
        );
    }

    private String buildTitle(AlertCaseEntity caseEntity, AlertRecordEntity latestRecord) {
        String objectRef = caseEntity.getDeviceId() != null && !caseEntity.getDeviceId().isBlank()
                ? caseEntity.getDeviceId()
                : caseEntity.getSegmentId() + "/" + caseEntity.getNodeId();
        return "Alert incident " + objectRef + " " + normalize(caseEntity.getRuleCode()) + " " + normalize(caseEntity.getCaseStatus());
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || (actual != null && actual.equalsIgnoreCase(expected.trim()));
    }

    private long safeVersion(Long versionNo) {
        return versionNo == null ? 0L : versionNo;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
