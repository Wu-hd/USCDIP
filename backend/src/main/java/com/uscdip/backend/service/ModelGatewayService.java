package com.uscdip.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.ModelGrayReleaseRequest;
import com.uscdip.backend.dto.ModelInferRequest;
import com.uscdip.backend.dto.ModelInvocationResponse;
import com.uscdip.backend.dto.ModelRegisterRequest;
import com.uscdip.backend.dto.ModelResponse;
import com.uscdip.backend.dto.ModelRollbackRequest;
import com.uscdip.backend.dto.ModelVersionCreateRequest;
import com.uscdip.backend.dto.ModelVersionResponse;
import com.uscdip.backend.entity.ModelInvocationAuditEntity;
import com.uscdip.backend.entity.ModelOperationAuditEntity;
import com.uscdip.backend.entity.ModelRegistryEntity;
import com.uscdip.backend.entity.ModelResultEntity;
import com.uscdip.backend.entity.ModelVersionEntity;
import com.uscdip.backend.entity.NodeEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.SegmentEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.ModelInvocationAuditRepository;
import com.uscdip.backend.repository.ModelOperationAuditRepository;
import com.uscdip.backend.repository.ModelRegistryRepository;
import com.uscdip.backend.repository.ModelResultRepository;
import com.uscdip.backend.repository.ModelVersionRepository;
import com.uscdip.backend.repository.NodeRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.SegmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ModelGatewayService {

    private static final String MENU_MODEL_READ = "MENU:MODEL:READ";
    private static final int DEFAULT_TIMEOUT_MS = 1500;
    private static final String MODEL_STATUS_REGISTERED = "REGISTERED";
    private static final String VERSION_DRAFT = "DRAFT";
    private static final String VERSION_GRAY = "GRAY";
    private static final String VERSION_ACTIVE = "ACTIVE";
    private static final String VERSION_ROLLED_BACK = "ROLLED_BACK";
    private static final String RESULT_SOURCE_MODEL = "MODEL";
    private static final String RESULT_SOURCE_RULE_FALLBACK = "RULE_FALLBACK";
    private static final String STATUS_SUCCESS = "SUCCESS";

    private final ModelRegistryRepository modelRegistryRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelInvocationAuditRepository modelInvocationAuditRepository;
    private final ModelOperationAuditRepository modelOperationAuditRepository;
    private final ModelResultRepository modelResultRepository;
    private final SegmentRepository segmentRepository;
    private final NodeRepository nodeRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final ObjectScopeService objectScopeService;
    private final RuleFallbackService ruleFallbackService;
    private final ObjectMapper objectMapper;

    public ModelGatewayService(
            ModelRegistryRepository modelRegistryRepository,
            ModelVersionRepository modelVersionRepository,
            ModelInvocationAuditRepository modelInvocationAuditRepository,
            ModelOperationAuditRepository modelOperationAuditRepository,
            ModelResultRepository modelResultRepository,
            SegmentRepository segmentRepository,
            NodeRepository nodeRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            ObjectScopeService objectScopeService,
            RuleFallbackService ruleFallbackService,
            ObjectMapper objectMapper
    ) {
        this.modelRegistryRepository = modelRegistryRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.modelInvocationAuditRepository = modelInvocationAuditRepository;
        this.modelOperationAuditRepository = modelOperationAuditRepository;
        this.modelResultRepository = modelResultRepository;
        this.segmentRepository = segmentRepository;
        this.nodeRepository = nodeRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.objectScopeService = objectScopeService;
        this.ruleFallbackService = ruleFallbackService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ModelResponse> listModels(int page, int pageSize, String status) {
        List<ModelRegistryEntity> models = modelRegistryRepository.findAllByOrderByUpdatedAtDescModelCodeAsc().stream()
                .filter(model -> matches(model.getStatus(), status))
                .toList();
        Map<String, List<ModelVersionEntity>> versionsByModel = modelVersionRepository.findByModelCodeIn(
                        models.stream().map(ModelRegistryEntity::getModelCode).toList()
                )
                .stream()
                .collect(Collectors.groupingBy(ModelVersionEntity::getModelCode, LinkedHashMap::new, Collectors.toList()));
        List<ModelResponse> items = models.stream()
                .map(model -> toResponse(model, versionsByModel.getOrDefault(model.getModelCode(), List.of())))
                .toList();
        return paginate(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public ModelResponse getModelDetail(String modelCode) {
        ModelRegistryEntity model = requireModel(modelCode);
        return toResponse(model, modelVersionRepository.findByModelCodeOrderByUpdatedAtDescVersionNoDesc(model.getModelCode()));
    }

    @Transactional
    public ModelResponse registerModel(AuthorizationContext context, ModelRegisterRequest request) {
        String modelCode = normalizeRequired(request.modelCode(), "modelCode");
        if (modelRegistryRepository.existsById(modelCode)) {
            throw conflict("Model already exists: " + modelCode);
        }
        LocalDateTime now = LocalDateTime.now();
        ModelRegistryEntity model = new ModelRegistryEntity(
                modelCode,
                request.modelName().trim(),
                defaultString(normalize(request.modelType()), "DIAGNOSIS"),
                MODEL_STATUS_REGISTERED,
                defaultInt(request.defaultTimeoutMs(), DEFAULT_TIMEOUT_MS),
                request.ruleFallbackEnabled() == null || request.ruleFallbackEnabled(),
                blankToNull(request.description()),
                context.userId(),
                now,
                now
        );
        modelRegistryRepository.save(model);
        recordOperation(modelCode, null, "REGISTER", context.userId(), null, snapshot(model), now);
        return toResponse(model, List.of());
    }

    @Transactional
    public ModelVersionResponse createVersion(AuthorizationContext context, String modelCode, ModelVersionCreateRequest request) {
        ModelRegistryEntity model = requireModel(modelCode);
        String versionNo = normalizeRequired(request.versionNo(), "versionNo");
        if (modelVersionRepository.existsByModelCodeAndVersionNo(model.getModelCode(), versionNo)) {
            throw conflict("Model version already exists: " + model.getModelCode() + ":" + versionNo);
        }
        LocalDateTime now = LocalDateTime.now();
        ModelVersionEntity version = new ModelVersionEntity(
                model.getModelCode() + ":" + versionNo,
                model.getModelCode(),
                versionNo,
                VERSION_DRAFT,
                0,
                blankToNull(request.artifactUri()),
                blankToNull(request.featureSchemaVersion()),
                defaultInt(request.timeoutMs(), defaultInt(model.getDefaultTimeoutMs(), DEFAULT_TIMEOUT_MS)),
                request.ruleFallbackEnabled() == null ? model.isRuleFallbackEnabled() : request.ruleFallbackEnabled(),
                null,
                null,
                context.userId(),
                now,
                now
        );
        modelVersionRepository.save(version);
        touchModel(model, now);
        recordOperation(model.getModelCode(), versionNo, "VERSION_CREATE", context.userId(), null, snapshot(version), now);
        return toVersionResponse(version);
    }

    @Transactional
    public ModelVersionResponse grayRelease(AuthorizationContext context, String modelCode, String versionNo, ModelGrayReleaseRequest request) {
        ModelRegistryEntity model = requireModel(modelCode);
        ModelVersionEntity version = requireVersion(model.getModelCode(), versionNo);
        LocalDateTime now = LocalDateTime.now();
        String before = snapshot(version);
        version.setStatus(VERSION_GRAY);
        version.setGrayPercent(request.grayPercent());
        version.setPublishedAt(now);
        version.setUpdatedAt(now);
        modelVersionRepository.save(version);
        touchModel(model, now);
        recordOperation(model.getModelCode(), version.getVersionNo(), "GRAY_PUBLISH", context.userId(), before, snapshot(version), now);
        return toVersionResponse(version);
    }

    @Transactional
    public ModelVersionResponse activateVersion(AuthorizationContext context, String modelCode, String versionNo) {
        ModelRegistryEntity model = requireModel(modelCode);
        ModelVersionEntity target = requireVersion(model.getModelCode(), versionNo);
        LocalDateTime now = LocalDateTime.now();
        String before = versionSetSnapshot(model.getModelCode());
        for (ModelVersionEntity version : modelVersionRepository.findByModelCodeOrderByUpdatedAtDescVersionNoDesc(model.getModelCode())) {
            if (VERSION_ACTIVE.equals(version.getStatus()) || VERSION_GRAY.equals(version.getStatus())) {
                version.setStatus(VERSION_ROLLED_BACK);
                version.setRolledBackAt(now);
                version.setUpdatedAt(now);
                modelVersionRepository.save(version);
            }
        }
        target.setStatus(VERSION_ACTIVE);
        target.setGrayPercent(100);
        target.setPublishedAt(now);
        target.setRolledBackAt(null);
        target.setUpdatedAt(now);
        modelVersionRepository.save(target);
        touchModel(model, now);
        recordOperation(model.getModelCode(), target.getVersionNo(), "ACTIVATE", context.userId(), before, versionSetSnapshot(model.getModelCode()), now);
        return toVersionResponse(target);
    }

    @Transactional
    public ModelVersionResponse rollback(AuthorizationContext context, String modelCode, ModelRollbackRequest request) {
        ModelRegistryEntity model = requireModel(modelCode);
        ModelVersionEntity target = resolveRollbackTarget(model.getModelCode(), request == null ? null : request.targetVersionNo());
        LocalDateTime now = LocalDateTime.now();
        String before = versionSetSnapshot(model.getModelCode());
        for (ModelVersionEntity version : modelVersionRepository.findByModelCodeOrderByUpdatedAtDescVersionNoDesc(model.getModelCode())) {
            if (VERSION_ACTIVE.equals(version.getStatus()) || VERSION_GRAY.equals(version.getStatus())) {
                version.setStatus(VERSION_ROLLED_BACK);
                version.setRolledBackAt(now);
                version.setUpdatedAt(now);
                modelVersionRepository.save(version);
            }
        }
        target.setStatus(VERSION_ACTIVE);
        target.setGrayPercent(100);
        target.setPublishedAt(now);
        target.setRolledBackAt(null);
        target.setUpdatedAt(now);
        modelVersionRepository.save(target);
        touchModel(model, now);
        recordOperation(model.getModelCode(), target.getVersionNo(), "ROLLBACK", context.userId(), before, versionSetSnapshot(model.getModelCode()), now);
        return toVersionResponse(target);
    }

    @Transactional
    public ModelInvocationResponse infer(AuthorizationContext context, String modelCode, ModelInferRequest request) {
        ModelRegistryEntity model = requireModel(modelCode);
        TargetSnapshot target = resolveTarget(request);
        objectScopeService.requireAccess(context, target.objectType(), target.objectId(), MENU_MODEL_READ);

        String requestId = defaultString(blankToNull(request.requestId()), "MREQ-" + UUID.randomUUID());
        LocalDateTime now = LocalDateTime.now();
        Optional<ModelVersionEntity> selectedVersion = selectVersion(model.getModelCode(), requestId);
        String failureReason = null;
        long latencyMs = request.simulateLatencyMs() == null ? 25L : request.simulateLatencyMs();

        if (selectedVersion.isEmpty()) {
            failureReason = "NO_ACTIVE_VERSION";
        } else if (Boolean.TRUE.equals(request.simulateFailure())) {
            failureReason = "MODEL_FAILED";
        } else if (latencyMs > defaultInt(selectedVersion.get().getTimeoutMs(), defaultInt(model.getDefaultTimeoutMs(), DEFAULT_TIMEOUT_MS))) {
            failureReason = "MODEL_TIMEOUT";
        }

        InvocationOutcome outcome = failureReason == null
                ? modelOutcome(model, selectedVersion.get(), target, requestId, request, latencyMs, now)
                : fallbackOutcome(model, selectedVersion.orElse(null), target, requestId, request, failureReason, latencyMs, now);
        recordOperation(model.getModelCode(), outcome.versionNo(), "INFER", context.userId(), null, outcome.outputSummary(), now);
        return new ModelInvocationResponse(
                requestId,
                model.getModelCode(),
                outcome.versionNo(),
                target.segmentId(),
                target.nodeId(),
                outcome.resultSource(),
                outcome.status(),
                outcome.modelResultId(),
                latencyMs,
                failureReason,
                outcome.outputSummary(),
                now
        );
    }

    private InvocationOutcome modelOutcome(
            ModelRegistryEntity model,
            ModelVersionEntity version,
            TargetSnapshot target,
            String requestId,
            ModelInferRequest request,
            long latencyMs,
            LocalDateTime now
    ) {
        String outputSummary = "modelCode=" + model.getModelCode()
                + ", versionNo=" + version.getVersionNo()
                + ", featureKeys=" + (request.features() == null ? 0 : request.features().size())
                + ", latencyMs=" + latencyMs;
        String resultId = saveModelResult(model.getModelCode(), version.getVersionNo(), "VALID", target, now);
        saveInvocationAudit(requestId, request, model.getModelCode(), version.getVersionNo(), target, RESULT_SOURCE_MODEL, STATUS_SUCCESS, resultId, latencyMs, null, outputSummary, now);
        return new InvocationOutcome(version.getVersionNo(), RESULT_SOURCE_MODEL, STATUS_SUCCESS, resultId, outputSummary);
    }

    private InvocationOutcome fallbackOutcome(
            ModelRegistryEntity model,
            ModelVersionEntity version,
            TargetSnapshot target,
            String requestId,
            ModelInferRequest request,
            String failureReason,
            long latencyMs,
            LocalDateTime now
    ) {
        RuleFallbackService.FallbackDecision decision = ruleFallbackService.evaluate(
                model.getModelCode(),
                target.segmentId(),
                target.nodeId(),
                failureReason,
                request.features()
        );
        String versionNo = version == null ? null : version.getVersionNo();
        String resultId = saveModelResult(model.getModelCode(), versionNo, RESULT_SOURCE_RULE_FALLBACK, target, now);
        saveInvocationAudit(requestId, request, model.getModelCode(), versionNo, target, decision.resultSource(), decision.status(), resultId, latencyMs, failureReason, decision.outputSummary(), now);
        return new InvocationOutcome(versionNo, decision.resultSource(), decision.status(), resultId, decision.outputSummary());
    }

    private String saveModelResult(String modelCode, String versionNo, String status, TargetSnapshot target, LocalDateTime now) {
        String resultId = "MR-B25-" + UUID.randomUUID();
        modelResultRepository.save(new ModelResultEntity(
                resultId,
                target.segmentId(),
                target.nodeId(),
                modelCode,
                versionNo,
                status,
                now,
                now
        ));
        syncModelResultScope(resultId, target, now);
        return resultId;
    }

    private void saveInvocationAudit(
            String requestId,
            ModelInferRequest request,
            String modelCode,
            String versionNo,
            TargetSnapshot target,
            String resultSource,
            String status,
            String resultId,
            long latencyMs,
            String failureReason,
            String outputSummary,
            LocalDateTime now
    ) {
        modelInvocationAuditRepository.save(new ModelInvocationAuditEntity(
                "MIA-" + UUID.randomUUID(),
                requestId,
                modelCode,
                versionNo,
                target.segmentId(),
                target.nodeId(),
                resultSource,
                status,
                resultId,
                latencyMs,
                failureReason,
                truncate(toJson(request.features()), 2000),
                truncate(outputSummary, 2000),
                now
        ));
    }

    private void syncModelResultScope(String resultId, TargetSnapshot target, LocalDateTime now) {
        ObjectScopeBindingEntity sourceBinding = objectScopeBindingRepository.findByObjectTypeAndObjectId(target.objectType(), target.objectId())
                .orElse(null);
        objectScopeBindingRepository.save(new ObjectScopeBindingEntity(
                "OSB-MR-B25-" + UUID.randomUUID(),
                ObjectScopeService.OBJECT_MODEL_RESULT,
                resultId,
                sourceBinding == null ? null : sourceBinding.getRegionId(),
                sourceBinding == null ? null : sourceBinding.getOwnerUserId(),
                sourceBinding == null ? null : sourceBinding.getOwnerUsername(),
                ObjectScopeService.LEVEL_MASKED,
                now,
                now
        ));
    }

    private Optional<ModelVersionEntity> selectVersion(String modelCode, String requestId) {
        List<ModelVersionEntity> versions = modelVersionRepository.findByModelCodeOrderByUpdatedAtDescVersionNoDesc(modelCode);
        Optional<ModelVersionEntity> gray = versions.stream()
                .filter(version -> VERSION_GRAY.equals(version.getStatus()))
                .max(Comparator.comparing(ModelVersionEntity::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        if (gray.isPresent() && shouldUseGray(requestId, gray.get().getGrayPercent())) {
            return gray;
        }
        Optional<ModelVersionEntity> active = versions.stream()
                .filter(version -> VERSION_ACTIVE.equals(version.getStatus()))
                .max(Comparator.comparing(ModelVersionEntity::getPublishedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return active.isPresent() ? active : gray;
    }

    private boolean shouldUseGray(String requestId, Integer grayPercent) {
        int percent = grayPercent == null ? 0 : Math.max(0, Math.min(100, grayPercent));
        if (percent >= 100) {
            return true;
        }
        if (percent <= 0) {
            return false;
        }
        return Math.floorMod(requestId.hashCode(), 100) < percent;
    }

    private TargetSnapshot resolveTarget(ModelInferRequest request) {
        if (request == null) {
            throw invalid("Request body is required");
        }
        String segmentId = blankToNull(request.segmentId());
        String nodeId = blankToNull(request.nodeId());
        if (segmentId == null && nodeId == null) {
            throw invalid("Either segmentId or nodeId is required");
        }
        if (segmentId != null) {
            SegmentEntity segment = segmentRepository.findById(segmentId)
                    .orElseThrow(() -> notFound(ErrorCode.SEGMENT_NOT_FOUND, "Segment not found: " + segmentId));
            return new TargetSnapshot(
                    segment.getSegmentId(),
                    defaultString(nodeId, segment.getStartNodeId()),
                    ObjectScopeService.OBJECT_SEGMENT,
                    segment.getSegmentId()
            );
        }
        NodeEntity node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> notFound(ErrorCode.NODE_NOT_FOUND, "Node not found: " + nodeId));
        String inferredSegmentId = segmentRepository.findByStartNodeIdOrEndNodeId(node.getNodeId(), node.getNodeId()).stream()
                .map(SegmentEntity::getSegmentId)
                .findFirst()
                .orElse(null);
        return new TargetSnapshot(inferredSegmentId, node.getNodeId(), ObjectScopeService.OBJECT_NODE, node.getNodeId());
    }

    private ModelRegistryEntity requireModel(String modelCode) {
        return modelRegistryRepository.findById(normalizeRequired(modelCode, "modelCode"))
                .orElseThrow(() -> notFound(ErrorCode.MODEL_NOT_FOUND, "Model not found: " + modelCode));
    }

    private ModelVersionEntity requireVersion(String modelCode, String versionNo) {
        return modelVersionRepository.findByModelCodeAndVersionNo(normalizeRequired(modelCode, "modelCode"), normalizeRequired(versionNo, "versionNo"))
                .orElseThrow(() -> notFound(ErrorCode.MODEL_VERSION_NOT_FOUND, "Model version not found: " + modelCode + ":" + versionNo));
    }

    private ModelVersionEntity resolveRollbackTarget(String modelCode, String targetVersionNo) {
        if (targetVersionNo != null && !targetVersionNo.isBlank()) {
            return requireVersion(modelCode, targetVersionNo);
        }
        return modelVersionRepository.findByModelCodeOrderByUpdatedAtDescVersionNoDesc(modelCode).stream()
                .filter(version -> VERSION_ROLLED_BACK.equals(version.getStatus()))
                .max(Comparator.comparing(ModelVersionEntity::getRolledBackAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> notFound(ErrorCode.MODEL_VERSION_NOT_FOUND, "No rolled back model version is available for: " + modelCode));
    }

    private void touchModel(ModelRegistryEntity model, LocalDateTime now) {
        model.setUpdatedAt(now);
        modelRegistryRepository.save(model);
    }

    private void recordOperation(String modelCode, String versionNo, String operationType, String operatorUserId, String beforeState, String afterState, LocalDateTime now) {
        modelOperationAuditRepository.save(new ModelOperationAuditEntity(
                "MOA-" + UUID.randomUUID(),
                modelCode,
                versionNo,
                operationType,
                operatorUserId,
                truncate(beforeState, 2000),
                truncate(afterState, 2000),
                now
        ));
    }

    private ModelResponse toResponse(ModelRegistryEntity model, List<ModelVersionEntity> versions) {
        List<ModelVersionEntity> sortedVersions = versions.stream()
                .sorted(Comparator.comparing(ModelVersionEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        String activeVersion = sortedVersions.stream()
                .filter(version -> VERSION_ACTIVE.equals(version.getStatus()))
                .map(ModelVersionEntity::getVersionNo)
                .findFirst()
                .orElse(null);
        String grayVersion = sortedVersions.stream()
                .filter(version -> VERSION_GRAY.equals(version.getStatus()))
                .map(ModelVersionEntity::getVersionNo)
                .findFirst()
                .orElse(null);
        return new ModelResponse(
                model.getModelCode(),
                model.getModelName(),
                model.getModelType(),
                model.getStatus(),
                model.getDefaultTimeoutMs(),
                model.isRuleFallbackEnabled(),
                model.getDescription(),
                activeVersion,
                grayVersion,
                model.getCreatedBy(),
                model.getCreatedAt(),
                model.getUpdatedAt(),
                sortedVersions.stream().map(this::toVersionResponse).toList()
        );
    }

    private ModelVersionResponse toVersionResponse(ModelVersionEntity version) {
        return new ModelVersionResponse(
                version.getVersionId(),
                version.getModelCode(),
                version.getVersionNo(),
                version.getStatus(),
                version.getGrayPercent(),
                version.getArtifactUri(),
                version.getFeatureSchemaVersion(),
                version.getTimeoutMs(),
                version.isRuleFallbackEnabled(),
                version.getPublishedAt(),
                version.getRolledBackAt(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }

    private PageResponse<ModelResponse> paginate(List<ModelResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        return PageResponse.of(items.subList(fromIndex, Math.min(fromIndex + safePageSize, items.size())), items.size(), safePage, safePageSize);
    }

    private String versionSetSnapshot(String modelCode) {
        return modelVersionRepository.findByModelCodeOrderByUpdatedAtDescVersionNoDesc(modelCode).stream()
                .map(version -> version.getVersionNo() + ":" + version.getStatus())
                .collect(Collectors.joining(","));
    }

    private String snapshot(Object value) {
        return truncate(toJson(value), 2000);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw invalid(fieldName + " is required");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || Objects.equals(normalize(actual), normalize(expected));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private AuthFlowException invalid(String message) {
        return new AuthFlowException(ErrorCode.MODEL_GATEWAY_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    private AuthFlowException conflict(String message) {
        return new AuthFlowException(ErrorCode.MODEL_GATEWAY_CONFLICT, HttpStatus.CONFLICT, message);
    }

    private AuthFlowException notFound(ErrorCode code, String message) {
        return new AuthFlowException(code, HttpStatus.NOT_FOUND, message);
    }

    private record TargetSnapshot(String segmentId, String nodeId, String objectType, String objectId) {
    }

    private record InvocationOutcome(String versionNo, String resultSource, String status, String modelResultId, String outputSummary) {
    }
}
