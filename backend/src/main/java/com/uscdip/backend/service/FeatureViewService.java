package com.uscdip.backend.service;

import com.uscdip.backend.dto.FeatureMetricViewResponse;
import com.uscdip.backend.dto.FeatureModelResultViewResponse;
import com.uscdip.backend.dto.FeatureViewAuditResponse;
import com.uscdip.backend.dto.FeatureViewGrantRequest;
import com.uscdip.backend.dto.FeatureViewGrantResponse;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.FeatureViewAccessAuditEntity;
import com.uscdip.backend.entity.FeatureViewGrantEntity;
import com.uscdip.backend.entity.ModelInvocationAuditEntity;
import com.uscdip.backend.entity.ModelResultEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.FeatureViewAccessAuditRepository;
import com.uscdip.backend.repository.FeatureViewGrantRepository;
import com.uscdip.backend.repository.ModelInvocationAuditRepository;
import com.uscdip.backend.repository.ModelResultRepository;
import com.uscdip.backend.repository.NodeRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.TsMetricRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeatureViewService {

    private static final String VIEW_MASKED = "MASKED";
    private static final String VIEW_DETAIL = "DETAIL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String TARGET_GLOBAL = "GLOBAL";
    private static final String TARGET_SEGMENT = "SEGMENT";
    private static final String TARGET_NODE = "NODE";
    private static final String TARGET_DEVICE = "DEVICE";
    private static final String QUERY_METRICS = "METRICS";
    private static final String QUERY_MODEL_RESULTS = "MODEL_RESULTS";

    private final FeatureViewGrantRepository grantRepository;
    private final FeatureViewAccessAuditRepository auditRepository;
    private final TsMetricRepository tsMetricRepository;
    private final DeviceRepository deviceRepository;
    private final NodeRepository nodeRepository;
    private final ModelResultRepository modelResultRepository;
    private final ModelInvocationAuditRepository modelInvocationAuditRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;

    public FeatureViewService(
            FeatureViewGrantRepository grantRepository,
            FeatureViewAccessAuditRepository auditRepository,
            TsMetricRepository tsMetricRepository,
            DeviceRepository deviceRepository,
            NodeRepository nodeRepository,
            ModelResultRepository modelResultRepository,
            ModelInvocationAuditRepository modelInvocationAuditRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository
    ) {
        this.grantRepository = grantRepository;
        this.auditRepository = auditRepository;
        this.tsMetricRepository = tsMetricRepository;
        this.deviceRepository = deviceRepository;
        this.nodeRepository = nodeRepository;
        this.modelResultRepository = modelResultRepository;
        this.modelInvocationAuditRepository = modelInvocationAuditRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
    }

    public PageResponse<FeatureMetricViewResponse> listMetricFeatures(
            AuthorizationContext context,
            int page,
            int pageSize,
            String requestedViewLevel,
            String segmentId,
            String nodeId,
            String deviceId,
            String metricCode,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        TargetHint targetHint = resolveMetricTargetHint(deviceId, segmentId, nodeId);
        ViewDecision decision = resolveViewDecision(context, normalizeView(requestedViewLevel), TARGET_DEVICE, deviceId, targetHint.segmentId(), targetHint.nodeId(), QUERY_METRICS);
        if (!decision.allowed()) {
            recordAudit(context, QUERY_METRICS, normalizeView(requestedViewLevel), VIEW_MASKED, "DENIED", null, 0, conditions(segmentId, nodeId, deviceId, metricCode, startTime, endTime), decision.reason());
            throw denied(decision.reason());
        }

        Map<String, DeviceEntity> deviceMap = deviceRepository.findAll().stream()
                .collect(Collectors.toMap(DeviceEntity::getDeviceId, Function.identity()));
        Map<String, NodeEntity> nodeMap = nodeRepository.findAll().stream()
                .collect(Collectors.toMap(NodeEntity::getNodeId, Function.identity()));
        Map<String, ObjectScopeBindingEntity> deviceBindings = objectScopeBindingRepository.findByObjectTypeAndObjectIdIn(
                        ObjectScopeService.OBJECT_DEVICE,
                        deviceMap.keySet()
                )
                .stream()
                .collect(Collectors.toMap(ObjectScopeBindingEntity::getObjectId, Function.identity(), (left, right) -> left));

        List<FeatureMetricViewResponse> rows = tsMetricRepository.findAll().stream()
                .sorted(Comparator.comparing(TsMetricEntity::getEventTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(metric -> matches(metric.getDeviceId(), deviceId))
                .filter(metric -> matches(metric.getMetricCode(), metricCode))
                .filter(metric -> inRange(metric.getEventTime(), startTime, endTime))
                .filter(metric -> {
                    DeviceEntity device = deviceMap.get(metric.getDeviceId());
                    return device != null && matches(device.getSegmentId(), segmentId) && matches(device.getNodeId(), nodeId);
                })
                .map(metric -> toMetricResponse(metric, deviceMap.get(metric.getDeviceId()), nodeMap, deviceBindings.get(metric.getDeviceId()), decision.effectiveViewLevel()))
                .toList();
        recordAudit(context, QUERY_METRICS, normalizeView(requestedViewLevel), decision.effectiveViewLevel(), "ALLOW", decision.grantId(), rows.size(), conditions(segmentId, nodeId, deviceId, metricCode, startTime, endTime), null);
        return paginate(rows, page, pageSize);
    }

    public PageResponse<FeatureModelResultViewResponse> listModelResultFeatures(
            AuthorizationContext context,
            int page,
            int pageSize,
            String requestedViewLevel,
            String modelCode,
            String segmentId,
            String nodeId,
            String status
    ) {
        ViewDecision decision = resolveViewDecision(context, normalizeView(requestedViewLevel), TARGET_SEGMENT, null, segmentId, nodeId, QUERY_MODEL_RESULTS);
        if (!decision.allowed()) {
            recordAudit(context, QUERY_MODEL_RESULTS, normalizeView(requestedViewLevel), VIEW_MASKED, "DENIED", null, 0, conditions(segmentId, nodeId, null, modelCode, null, null), decision.reason());
            throw denied(decision.reason());
        }

        List<ModelResultEntity> allResults = modelResultRepository.findAll();
        Map<String, ModelInvocationAuditEntity> auditByResultId = modelInvocationAuditRepository.findByModelResultIdIn(
                        allResults.stream().map(ModelResultEntity::getModelResultId).toList()
                )
                .stream()
                .collect(Collectors.toMap(ModelInvocationAuditEntity::getModelResultId, Function.identity(), (left, right) -> left));
        Map<String, ObjectScopeBindingEntity> resultBindings = objectScopeBindingRepository.findByObjectTypeAndObjectIdIn(
                        ObjectScopeService.OBJECT_MODEL_RESULT,
                        allResults.stream().map(ModelResultEntity::getModelResultId).toList()
                )
                .stream()
                .collect(Collectors.toMap(ObjectScopeBindingEntity::getObjectId, Function.identity(), (left, right) -> left));

        List<FeatureModelResultViewResponse> rows = allResults.stream()
                .sorted(Comparator.comparing(ModelResultEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(result -> matches(result.getModelCode(), modelCode))
                .filter(result -> matches(result.getSegmentId(), segmentId))
                .filter(result -> matches(result.getNodeId(), nodeId))
                .filter(result -> matches(result.getStatus(), status))
                .map(result -> toModelResultResponse(result, auditByResultId.get(result.getModelResultId()), resultBindings.get(result.getModelResultId()), decision.effectiveViewLevel()))
                .toList();
        recordAudit(context, QUERY_MODEL_RESULTS, normalizeView(requestedViewLevel), decision.effectiveViewLevel(), "ALLOW", decision.grantId(), rows.size(), conditions(segmentId, nodeId, null, modelCode, null, null), null);
        return paginate(rows, page, pageSize);
    }

    @Transactional
    public FeatureViewGrantResponse createGrant(AuthorizationContext context, FeatureViewGrantRequest request) {
        String targetType = normalizeTarget(request.targetType());
        String viewLevel = normalizeView(request.viewLevel());
        if (!VIEW_DETAIL.equals(viewLevel)) {
            throw invalid("Only DETAIL grants are supported in B-26");
        }
        if (!TARGET_GLOBAL.equals(targetType) && (request.targetId() == null || request.targetId().isBlank())) {
            throw invalid("targetId is required unless targetType is GLOBAL");
        }
        LocalDateTime now = LocalDateTime.now();
        FeatureViewGrantEntity entity = new FeatureViewGrantEntity(
                "FVG-" + UUID.randomUUID(),
                request.userId().trim(),
                viewLevel,
                targetType,
                TARGET_GLOBAL.equals(targetType) ? null : request.targetId().trim(),
                request.reason().trim(),
                context.userId(),
                request.expiresAt(),
                STATUS_ACTIVE,
                now,
                null
        );
        return toGrantResponse(grantRepository.save(entity));
    }

    @Transactional
    public FeatureViewGrantResponse revokeGrant(AuthorizationContext context, String grantId) {
        FeatureViewGrantEntity grant = grantRepository.findById(grantId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.FEATURE_VIEW_GRANT_NOT_FOUND, HttpStatus.NOT_FOUND, "Feature view grant not found: " + grantId));
        grant.setStatus(STATUS_REVOKED);
        grant.setRevokedAt(LocalDateTime.now());
        return toGrantResponse(grantRepository.save(grant));
    }

    public PageResponse<FeatureViewAuditResponse> listAudits(int page, int pageSize, String userId, String decision) {
        List<FeatureViewAuditResponse> items = auditRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(audit -> matches(audit.getUserId(), userId))
                .filter(audit -> matches(audit.getDecision(), decision))
                .map(this::toAuditResponse)
                .toList();
        return paginate(items, page, pageSize);
    }

    private ViewDecision resolveViewDecision(
            AuthorizationContext context,
            String requestedViewLevel,
            String targetType,
            String deviceId,
            String segmentId,
            String nodeId,
            String queryType
    ) {
        if (VIEW_MASKED.equals(requestedViewLevel)) {
            return new ViewDecision(true, VIEW_MASKED, null, null);
        }
        List<FeatureViewGrantEntity> grants = grantRepository.findByUserIdAndViewLevelAndStatusAndExpiresAtAfter(
                context.userId(),
                VIEW_DETAIL,
                STATUS_ACTIVE,
                LocalDateTime.now()
        );
        Optional<FeatureViewGrantEntity> matchedGrant = grants.stream()
                .filter(grant -> grantMatches(grant, targetType, deviceId, segmentId, nodeId))
                .findFirst();
        return matchedGrant
                .map(grant -> new ViewDecision(true, VIEW_DETAIL, grant.getGrantId(), null))
                .orElseGet(() -> new ViewDecision(false, VIEW_MASKED, null, "DETAIL view requires an active time-limited grant"));
    }

    private TargetHint resolveMetricTargetHint(String deviceId, String segmentId, String nodeId) {
        if (deviceId != null && !deviceId.isBlank()) {
            return deviceRepository.findById(deviceId.trim())
                    .map(device -> new TargetHint(
                            defaultString(segmentId, device.getSegmentId()),
                            defaultString(nodeId, device.getNodeId())
                    ))
                    .orElseGet(() -> new TargetHint(segmentId, nodeId));
        }
        return new TargetHint(segmentId, nodeId);
    }

    private boolean grantMatches(FeatureViewGrantEntity grant, String targetType, String deviceId, String segmentId, String nodeId) {
        if (TARGET_GLOBAL.equals(grant.getTargetType())) {
            return true;
        }
        String grantTargetId = grant.getTargetId();
        if (TARGET_DEVICE.equals(grant.getTargetType())) {
            return grantTargetId != null && grantTargetId.equalsIgnoreCase(deviceId);
        }
        if (TARGET_SEGMENT.equals(grant.getTargetType())) {
            return grantTargetId != null && grantTargetId.equalsIgnoreCase(segmentId);
        }
        if (TARGET_NODE.equals(grant.getTargetType())) {
            return grantTargetId != null && grantTargetId.equalsIgnoreCase(nodeId);
        }
        return grant.getTargetType().equalsIgnoreCase(targetType) && grantTargetId != null;
    }

    private FeatureMetricViewResponse toMetricResponse(
            TsMetricEntity metric,
            DeviceEntity device,
            Map<String, NodeEntity> nodeMap,
            ObjectScopeBindingEntity binding,
            String viewLevel
    ) {
        NodeEntity node = device == null ? null : nodeMap.get(device.getNodeId());
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("metricValue", metric.getMetricValue());
        features.put("dqScore", metric.getDqScore());
        features.put("dqAlarmConfFactor", metric.getDqAlarmConfFactor());
        features.put("backfill", metric.isBackfill());
        features.put("lateArrival", metric.isLateArrival());
        features.put("latencyMs", metric.getLatencyMs());
        if (VIEW_DETAIL.equals(viewLevel)) {
            return new FeatureMetricViewResponse(
                    null,
                    metric.getSourceRecordId(),
                    metric.getDeviceId(),
                    device == null ? null : device.getDeviceName(),
                    null,
                    device == null ? null : device.getSegmentId(),
                    device == null ? null : device.getNodeId(),
                    binding == null ? null : binding.getRegionId(),
                    metric.getMetricCode(),
                    metric.getMetricValue(),
                    metric.getEventTime(),
                    timeBucket(metric.getEventTime()),
                    metric.getDqScore(),
                    metric.getDqLevel(),
                    metric.getDqFlags(),
                    node == null ? null : node.getGeometry2d(),
                    null,
                    features
            );
        }
        String featureRecordId = stableHash("metric:" + metric.getSourceRecordId());
        return new FeatureMetricViewResponse(
                featureRecordId,
                null,
                null,
                null,
                stableHash("device:" + metric.getDeviceId()),
                null,
                null,
                binding == null ? null : binding.getRegionId(),
                metric.getMetricCode(),
                metric.getMetricValue(),
                null,
                timeBucket(metric.getEventTime()),
                metric.getDqScore(),
                metric.getDqLevel(),
                metric.getDqFlags(),
                null,
                node == null ? null : "GRID-" + stableHash(node.getGeometry2d()).substring(0, 8),
                features
        );
    }

    private FeatureModelResultViewResponse toModelResultResponse(
            ModelResultEntity result,
            ModelInvocationAuditEntity invocationAudit,
            ObjectScopeBindingEntity binding,
            String viewLevel
    ) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("modelCode", result.getModelCode());
        features.put("modelVersion", result.getModelVersion());
        features.put("status", result.getStatus());
        features.put("resultSource", invocationAudit == null ? null : invocationAudit.getResultSource());
        features.put("latencyMs", invocationAudit == null ? null : invocationAudit.getLatencyMs());
        if (VIEW_DETAIL.equals(viewLevel)) {
            return new FeatureModelResultViewResponse(
                    null,
                    result.getModelResultId(),
                    result.getSegmentId(),
                    result.getNodeId(),
                    binding == null ? null : binding.getRegionId(),
                    result.getModelCode(),
                    result.getModelVersion(),
                    result.getStatus(),
                    invocationAudit == null ? null : invocationAudit.getResultSource(),
                    invocationAudit == null ? null : invocationAudit.getFailureReason(),
                    result.getCreatedAt(),
                    features
            );
        }
        return new FeatureModelResultViewResponse(
                stableHash("model-result:" + result.getModelResultId()),
                null,
                null,
                null,
                binding == null ? null : binding.getRegionId(),
                result.getModelCode(),
                result.getModelVersion(),
                result.getStatus(),
                invocationAudit == null ? null : invocationAudit.getResultSource(),
                null,
                timeBucket(result.getCreatedAt()),
                features
        );
    }

    private void recordAudit(
            AuthorizationContext context,
            String queryType,
            String requestedViewLevel,
            String effectiveViewLevel,
            String decision,
            String grantId,
            int resultCount,
            String queryConditions,
            String reason
    ) {
        auditRepository.save(new FeatureViewAccessAuditEntity(
                "FVA-" + UUID.randomUUID(),
                context.userId(),
                context.username(),
                queryType,
                requestedViewLevel,
                effectiveViewLevel,
                decision,
                grantId,
                resultCount,
                queryConditions,
                reason,
                LocalDateTime.now()
        ));
    }

    private FeatureViewGrantResponse toGrantResponse(FeatureViewGrantEntity grant) {
        return new FeatureViewGrantResponse(
                grant.getGrantId(),
                grant.getUserId(),
                grant.getViewLevel(),
                grant.getTargetType(),
                grant.getTargetId(),
                grant.getReason(),
                grant.getGrantedBy(),
                grant.getExpiresAt(),
                grant.getStatus(),
                grant.getCreatedAt(),
                grant.getRevokedAt()
        );
    }

    private FeatureViewAuditResponse toAuditResponse(FeatureViewAccessAuditEntity audit) {
        return new FeatureViewAuditResponse(
                audit.getAuditId(),
                audit.getUserId(),
                audit.getUsername(),
                audit.getQueryType(),
                audit.getRequestedViewLevel(),
                audit.getEffectiveViewLevel(),
                audit.getDecision(),
                audit.getGrantId(),
                audit.getResultCount(),
                audit.getQueryConditions(),
                audit.getReason(),
                audit.getCreatedAt()
        );
    }

    private <T> PageResponse<T> paginate(List<T> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private String normalizeView(String value) {
        if (value == null || value.isBlank()) {
            return VIEW_MASKED;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!VIEW_MASKED.equals(normalized) && !VIEW_DETAIL.equals(normalized)) {
            throw invalid("viewLevel must be MASKED or DETAIL");
        }
        return normalized;
    }

    private String normalizeTarget(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of(TARGET_GLOBAL, TARGET_SEGMENT, TARGET_NODE, TARGET_DEVICE).contains(normalized)) {
            throw invalid("targetType must be GLOBAL, SEGMENT, NODE or DEVICE");
        }
        return normalized;
    }

    private boolean inRange(LocalDateTime eventTime, LocalDateTime startTime, LocalDateTime endTime) {
        return eventTime != null
                && (startTime == null || !eventTime.isBefore(startTime))
                && (endTime == null || !eventTime.isAfter(endTime));
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || (actual != null && actual.equalsIgnoreCase(expected.trim()));
    }

    private LocalDateTime timeBucket(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.HOURS);
    }

    private String conditions(String segmentId, String nodeId, String deviceId, String metricOrModel, LocalDateTime startTime, LocalDateTime endTime) {
        return "segmentId=" + nullToBlank(segmentId)
                + ", nodeId=" + nullToBlank(nodeId)
                + ", deviceId=" + nullToBlank(deviceId)
                + ", metricOrModel=" + nullToBlank(metricOrModel)
                + ", startTime=" + Objects.toString(startTime, "")
                + ", endTime=" + Objects.toString(endTime, "");
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stableHash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12 && i < bytes.length; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private AuthFlowException denied(String message) {
        return new AuthFlowException(ErrorCode.FEATURE_VIEW_DENIED, HttpStatus.FORBIDDEN, message);
    }

    private AuthFlowException invalid(String message) {
        return new AuthFlowException(ErrorCode.FEATURE_VIEW_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    private record ViewDecision(boolean allowed, String effectiveViewLevel, String grantId, String reason) {
    }

    private record TargetHint(String segmentId, String nodeId) {
    }
}
