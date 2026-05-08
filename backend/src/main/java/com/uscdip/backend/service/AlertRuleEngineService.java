package com.uscdip.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.AlertCaseProcessingSummary;
import com.uscdip.backend.dto.AlertEvaluateRequest;
import com.uscdip.backend.dto.AlertEvaluateResponse;
import com.uscdip.backend.dto.AlertRecordResponse;
import com.uscdip.backend.dto.AlertRuleResponse;
import com.uscdip.backend.entity.AlertRecordEntity;
import com.uscdip.backend.entity.AlertRuleEntity;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.AlertRecordRepository;
import com.uscdip.backend.repository.AlertRuleRepository;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.TsMetricRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertRuleEngineService {

    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final String MENU_ASSET_WRITE = "MENU:ASSET:WRITE";
    private static final String RULE_TYPE_THRESHOLD = "THRESHOLD";
    private static final String RULE_TYPE_COMPOSITE = "COMPOSITE";
    private static final String LOGIC_ANY = "ANY";
    private static final String LOGIC_ALL = "ALL";
    private static final String METRIC_DQ_SCORE = "DQ_SCORE";
    private static final String DECISION_TRIGGERED = "TRIGGERED";
    private static final String DECISION_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    private static final String DECISION_DQ_BLOCKED = "DQ_BLOCKED";

    private final AlertRuleRepository alertRuleRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final TsMetricRepository tsMetricRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectScopeService objectScopeService;
    private final AlertCaseLifecycleService alertCaseLifecycleService;
    private final ObjectMapper objectMapper;

    public AlertRuleEngineService(
            AlertRuleRepository alertRuleRepository,
            AlertRecordRepository alertRecordRepository,
            TsMetricRepository tsMetricRepository,
            DeviceRepository deviceRepository,
            ObjectScopeService objectScopeService,
            AlertCaseLifecycleService alertCaseLifecycleService,
            ObjectMapper objectMapper
    ) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.tsMetricRepository = tsMetricRepository;
        this.deviceRepository = deviceRepository;
        this.objectScopeService = objectScopeService;
        this.alertCaseLifecycleService = alertCaseLifecycleService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AlertEvaluateResponse evaluate(
            AuthorizationContext context,
            AlertEvaluateRequest request
    ) {
        List<TsMetricEntity> metrics = resolveMetricsForRequest(request);
        metrics.forEach(metric -> objectScopeService.requireAccess(
                context,
                ObjectScopeService.OBJECT_DEVICE,
                metric.getDeviceId(),
                MENU_ASSET_WRITE
        ));
        EvaluationOutcome outcome = evaluateMetrics(metrics, request == null ? null : request.ruleCodes(), true);
        return new AlertEvaluateResponse(
                metrics.size(),
                outcome.evaluatedRuleCount(),
                outcome.records().size(),
                outcome.processingSummary().openedCaseCount(),
                outcome.processingSummary().dedupedCount(),
                outcome.processingSummary().suppressedCount(),
                outcome.processingSummary().escalatedCount(),
                outcome.records().stream().map(this::toRecordResponse).toList()
        );
    }

    @Transactional
    public List<AlertRecordResponse> autoEvaluateBatch(String sourceBatchId) {
        if (sourceBatchId == null || sourceBatchId.isBlank()) {
            return List.of();
        }
        if (alertRuleRepository.findByEnabledTrueOrderByRuleCodeAsc().isEmpty()) {
            return List.of();
        }
        List<TsMetricEntity> metrics = tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(sourceBatchId);
        if (metrics.isEmpty()) {
            return List.of();
        }
        return evaluateMetrics(metrics, null, true).records().stream().map(this::toRecordResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertRecordResponse> listAlerts(
            AuthorizationContext context,
            int page,
            int pageSize,
            String deviceId,
            String ruleCode,
            String decision,
            String severity,
            String sourceBatchId
    ) {
        List<AlertRecordEntity> visible = filterVisibleAlertRecords(context, alertRecordRepository.findAllByOrderByCreatedAtDescAlertIdDesc());
        List<AlertRecordResponse> items = visible.stream()
                .filter(record -> matches(record.getDeviceId(), deviceId))
                .filter(record -> matches(record.getRuleCode(), ruleCode))
                .filter(record -> matches(record.getDecision(), decision))
                .filter(record -> matches(record.getSeverity(), severity))
                .filter(record -> matches(record.getSourceBatchId(), sourceBatchId))
                .map(this::toRecordResponse)
                .toList();
        return paginate(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public AlertRecordResponse getAlertDetail(AuthorizationContext context, String alertId) {
        AlertRecordEntity alert = alertRecordRepository.findById(alertId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.ALERT_RECORD_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Alert record not found: " + alertId
                ));
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, alert.getDeviceId(), MENU_ASSET_READ);
        return toRecordResponse(alert);
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listEnabledRules() {
        return alertRuleRepository.findByEnabledTrueOrderByRuleCodeAsc().stream()
                .map(this::toRuleResponse)
                .toList();
    }

    private EvaluationOutcome evaluateMetrics(List<TsMetricEntity> metrics, List<String> requestedRuleCodes, boolean persist) {
        List<AlertRuleEntity> rules = resolveRules(requestedRuleCodes);
        Map<String, AlertRuleEntity> ruleMap = alertRuleRepository.findAllByOrderByRuleCodeAsc().stream()
                .collect(Collectors.toMap(AlertRuleEntity::getRuleCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, DeviceEntity> deviceMap = loadDevices(metrics);
        List<AlertRecordEntity> toPersist = new ArrayList<>();

        for (TsMetricEntity metric : metrics) {
            for (AlertRuleEntity rule : rules) {
                RuleHit hit = evaluateRule(metric, rule, ruleMap, new ArrayDeque<>());
                if (hit == null) {
                    continue;
                }
                DeviceEntity device = deviceMap.get(metric.getDeviceId());
                LocalDateTime now = LocalDateTime.now();
                toPersist.add(new AlertRecordEntity(
                        "ALERT-" + UUID.randomUUID(),
                        metric.getSourceRecordId(),
                        metric.getSourceBatchId(),
                        metric.getDeviceId(),
                        device == null ? null : device.getSegmentId(),
                        device == null ? null : device.getNodeId(),
                        rule.getRuleCode(),
                        normalizeNullable(rule.getSeverity()),
                        resolveDecision(metric.getDqScore()),
                        round(hit.rawConfidence()),
                        round(hit.finalConfidence()),
                        metric.getDqScore(),
                        metric.getDqLevel(),
                        metric.getDqAlarmConfFactor(),
                        hit.metricCode(),
                        hit.metricValue(),
                        metric.getEventTime(),
                        metric.getTraceId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        now
                ));
            }
        }

        List<AlertRecordEntity> saved = persist && !toPersist.isEmpty() ? alertRecordRepository.saveAll(toPersist) : toPersist;
        AlertCaseProcessingSummary processingSummary = persist && !saved.isEmpty()
                ? alertCaseLifecycleService.processAlerts(saved)
                : AlertCaseProcessingSummary.empty();
        return new EvaluationOutcome(saved, rules.size(), processingSummary);
    }

    private List<TsMetricEntity> resolveMetricsForRequest(AlertEvaluateRequest request) {
        if (request == null) {
            throw invalidEvaluation("Request body is required");
        }
        boolean hasRecordIds = request.sourceRecordIds() != null && !request.sourceRecordIds().isEmpty();
        boolean hasBatchId = request.sourceBatchId() != null && !request.sourceBatchId().isBlank();
        if (hasRecordIds == hasBatchId) {
            throw invalidEvaluation("Exactly one of sourceRecordIds or sourceBatchId must be provided");
        }
        List<TsMetricEntity> metrics = hasRecordIds
                ? tsMetricRepository.findAllById(request.sourceRecordIds())
                : tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(request.sourceBatchId().trim());
        if (metrics.isEmpty()) {
            throw invalidEvaluation("No scored metrics found for alert evaluation");
        }
        if (hasRecordIds && metrics.size() != request.sourceRecordIds().size()) {
            throw invalidEvaluation("One or more sourceRecordIds were not found");
        }
        return metrics.stream()
                .sorted(Comparator.comparing(TsMetricEntity::getEventTime).thenComparing(TsMetricEntity::getSourceRecordId))
                .toList();
    }

    private List<AlertRuleEntity> resolveRules(List<String> requestedRuleCodes) {
        List<AlertRuleEntity> rules;
        if (requestedRuleCodes == null || requestedRuleCodes.isEmpty()) {
            rules = alertRuleRepository.findByEnabledTrueOrderByRuleCodeAsc();
        } else {
            List<String> normalizedRuleCodes = requestedRuleCodes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();
            if (normalizedRuleCodes.isEmpty()) {
                throw invalidEvaluation("ruleCodes must not be blank when provided");
            }
            Map<String, AlertRuleEntity> found = alertRuleRepository.findByRuleCodeIn(normalizedRuleCodes).stream()
                    .collect(Collectors.toMap(rule -> normalizeNullable(rule.getRuleCode()), Function.identity()));
            List<String> missing = normalizedRuleCodes.stream().filter(code -> !found.containsKey(code)).toList();
            if (!missing.isEmpty()) {
                throw new AuthFlowException(
                        ErrorCode.ALERT_RULE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Alert rules not found: " + String.join(",", missing)
                );
            }
            rules = normalizedRuleCodes.stream().map(found::get).toList();
        }
        if (rules.isEmpty()) {
            throw new AuthFlowException(
                    ErrorCode.ALERT_RULE_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "No enabled alert rules found"
            );
        }
        return rules;
    }

    private RuleHit evaluateRule(
            TsMetricEntity metric,
            AlertRuleEntity rule,
            Map<String, AlertRuleEntity> ruleMap,
            Deque<String> stack
    ) {
        if (rule == null) {
            return null;
        }
        String ruleCode = normalizeNullable(rule.getRuleCode());
        if (stack.contains(ruleCode)) {
            throw invalidRule("Circular alert rule reference detected: " + String.join(" -> ", stack) + " -> " + ruleCode);
        }
        stack.push(ruleCode);
        try {
            String ruleType = normalizeNullable(rule.getRuleType());
            if (RULE_TYPE_THRESHOLD.equals(ruleType)) {
                return evaluateThresholdRule(metric, rule);
            }
            if (RULE_TYPE_COMPOSITE.equals(ruleType)) {
                return evaluateCompositeRule(metric, rule, ruleMap, stack);
            }
            throw invalidRule("Unsupported alert rule type: " + rule.getRuleType());
        } finally {
            stack.pop();
        }
    }

    private RuleHit evaluateThresholdRule(TsMetricEntity metric, AlertRuleEntity rule) {
        String metricCode = normalizeNullable(rule.getMetricCode());
        String operator = normalizeNullable(rule.getOperator());
        if (metricCode == null || operator == null) {
            throw invalidRule("Threshold rule is missing metricCode or operator: " + rule.getRuleCode());
        }
        EvaluationValue evaluationValue = resolveEvaluationValue(metric, metricCode);
        if (!evaluationValue.applicable()) {
            return null;
        }
        Double numericValue = evaluationValue.numericValue();
        if (numericValue == null) {
            throw invalidRule("Threshold rule metric is not numeric: " + rule.getRuleCode());
        }
        boolean matched = switch (operator) {
            case "GT" -> requireThreshold(rule.getThresholdLow(), rule.getRuleCode(), "thresholdLow") < numericValue;
            case "GTE" -> requireThreshold(rule.getThresholdLow(), rule.getRuleCode(), "thresholdLow") <= numericValue;
            case "LT" -> numericValue < requireThreshold(rule.getThresholdLow(), rule.getRuleCode(), "thresholdLow");
            case "LTE" -> numericValue <= requireThreshold(rule.getThresholdLow(), rule.getRuleCode(), "thresholdLow");
            case "BETWEEN" -> {
                double low = requireThreshold(rule.getThresholdLow(), rule.getRuleCode(), "thresholdLow");
                double high = requireThreshold(rule.getThresholdHigh(), rule.getRuleCode(), "thresholdHigh");
                yield numericValue >= low && numericValue <= high;
            }
            default -> throw invalidRule("Unsupported threshold operator: " + rule.getOperator());
        };
        if (!matched) {
            return null;
        }
        double rawConfidence = requireThreshold(rule.getBaseConfidence(), rule.getRuleCode(), "baseConfidence");
        double factor = metric.getDqScore() == null ? 0.5d : 0.5d + (0.5d * metric.getDqScore() / 100.0d);
        return new RuleHit(
                rawConfidence,
                rawConfidence * factor,
                evaluationValue.metricCode(),
                evaluationValue.displayValue()
        );
    }

    private RuleHit evaluateCompositeRule(
            TsMetricEntity metric,
            AlertRuleEntity rule,
            Map<String, AlertRuleEntity> ruleMap,
            Deque<String> stack
    ) {
        String logicType = normalizeNullable(rule.getLogicType());
        if (!LOGIC_ANY.equals(logicType) && !LOGIC_ALL.equals(logicType)) {
            throw invalidRule("Composite rule logicType must be ANY or ALL: " + rule.getRuleCode());
        }
        List<String> childRuleCodes = parseChildRuleCodes(rule);
        List<RuleHit> childHits = new ArrayList<>();
        for (String childRuleCode : childRuleCodes) {
            AlertRuleEntity childRule = ruleMap.get(childRuleCode);
            if (childRule == null) {
                throw invalidRule("Composite rule child not found: " + childRuleCode + " for " + rule.getRuleCode());
            }
            RuleHit hit = evaluateRule(metric, childRule, ruleMap, stack);
            if (hit != null) {
                childHits.add(hit);
            } else if (LOGIC_ALL.equals(logicType)) {
                return null;
            }
        }
        if (childHits.isEmpty()) {
            return null;
        }
        RuleHit representative = LOGIC_ANY.equals(logicType)
                ? childHits.stream().max(Comparator.comparingDouble(RuleHit::rawConfidence)).orElseThrow()
                : childHits.stream().min(Comparator.comparingDouble(RuleHit::rawConfidence)).orElseThrow();
        double rawConfidence = LOGIC_ANY.equals(logicType)
                ? childHits.stream().mapToDouble(RuleHit::rawConfidence).max().orElse(0.0)
                : childHits.stream().mapToDouble(RuleHit::rawConfidence).min().orElse(0.0);
        double factor = metric.getDqScore() == null ? 0.5d : 0.5d + (0.5d * metric.getDqScore() / 100.0d);
        return new RuleHit(
                rawConfidence,
                rawConfidence * factor,
                representative.metricCode(),
                representative.metricValue()
        );
    }

    private EvaluationValue resolveEvaluationValue(TsMetricEntity metric, String ruleMetricCode) {
        if (METRIC_DQ_SCORE.equals(ruleMetricCode)) {
            return new EvaluationValue(true, metric.getDqScore(), METRIC_DQ_SCORE, metric.getDqScore() == null ? null : String.valueOf(round(metric.getDqScore())));
        }
        if (!ruleMetricCode.equalsIgnoreCase(metric.getMetricCode())) {
            return new EvaluationValue(false, null, null, null);
        }
        Double numericValue = parseNumeric(metric.getMetricValue());
        return new EvaluationValue(true, numericValue, normalizeNullable(metric.getMetricCode()), metric.getMetricValue());
    }

    private List<String> parseChildRuleCodes(AlertRuleEntity rule) {
        if (rule.getExpressionJson() == null || rule.getExpressionJson().isBlank()) {
            throw invalidRule("Composite rule expressionJson is required: " + rule.getRuleCode());
        }
        try {
            JsonNode root = objectMapper.readTree(rule.getExpressionJson());
            JsonNode childRuleCodes = root.path("childRuleCodes");
            if (!childRuleCodes.isArray() || childRuleCodes.isEmpty()) {
                throw invalidRule("Composite rule childRuleCodes is required: " + rule.getRuleCode());
            }
            List<String> result = new ArrayList<>();
            childRuleCodes.forEach(node -> {
                if (node != null && !node.asText().isBlank()) {
                    result.add(node.asText().trim().toUpperCase(Locale.ROOT));
                }
            });
            if (result.isEmpty()) {
                throw invalidRule("Composite rule childRuleCodes is empty: " + rule.getRuleCode());
            }
            return result;
        } catch (AuthFlowException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidRule("Composite rule expressionJson is invalid for " + rule.getRuleCode());
        }
    }

    private Map<String, DeviceEntity> loadDevices(Collection<TsMetricEntity> metrics) {
        Set<String> deviceIds = metrics.stream()
                .map(TsMetricEntity::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return deviceRepository.findAllById(deviceIds).stream()
                .collect(Collectors.toMap(DeviceEntity::getDeviceId, Function.identity()));
    }

    private List<AlertRecordEntity> filterVisibleAlertRecords(AuthorizationContext context, List<AlertRecordEntity> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<String> accessibleDeviceIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_DEVICE,
                records.stream().map(AlertRecordEntity::getDeviceId).collect(Collectors.toCollection(LinkedHashSet::new)),
                MENU_ASSET_READ
        );
        return records.stream()
                .filter(record -> accessibleDeviceIds.contains(record.getDeviceId()))
                .toList();
    }

    private PageResponse<AlertRecordResponse> paginate(List<AlertRecordResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || (actual != null && actual.equalsIgnoreCase(expected.trim()));
    }

    private String resolveDecision(Double dqScore) {
        double score = dqScore == null ? 0.0d : dqScore;
        if (score >= 70.0d) {
            return DECISION_TRIGGERED;
        }
        if (score >= 60.0d) {
            return DECISION_REVIEW_REQUIRED;
        }
        return DECISION_DQ_BLOCKED;
    }

    private double requireThreshold(Double value, String ruleCode, String fieldName) {
        if (value == null) {
            throw invalidRule("Alert rule " + ruleCode + " is missing " + fieldName);
        }
        return value;
    }

    private Double parseNumeric(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private AlertRuleResponse toRuleResponse(AlertRuleEntity entity) {
        return new AlertRuleResponse(
                entity.getRuleId(),
                entity.getRuleCode(),
                entity.getRuleName(),
                entity.getRuleType(),
                entity.getMetricCode(),
                entity.getOperator(),
                entity.getThresholdLow(),
                entity.getThresholdHigh(),
                entity.getLogicType(),
                entity.getBaseConfidence(),
                entity.getSeverity(),
                entity.isEnabled(),
                entity.getExpressionJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AlertRecordResponse toRecordResponse(AlertRecordEntity entity) {
        return new AlertRecordResponse(
                entity.getAlertId(),
                entity.getSourceRecordId(),
                entity.getSourceBatchId(),
                entity.getDeviceId(),
                entity.getSegmentId(),
                entity.getNodeId(),
                entity.getRuleCode(),
                entity.getSeverity(),
                entity.getDecision(),
                entity.getAlertConfRaw(),
                entity.getAlertConfFinal(),
                entity.getDqScoreSnapshot(),
                entity.getDqLevelSnapshot(),
                entity.getDqAlarmConfFactor(),
                entity.getMetricCode(),
                entity.getMetricValue(),
                entity.getEventTime(),
                entity.getTraceId(),
                entity.getCaseId(),
                entity.getDedupeKey(),
                entity.getProcessStatus(),
                entity.getSuppressed(),
                entity.getEscalationLevel(),
                entity.getProcessedAt(),
                entity.getCreatedAt()
        );
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private AuthFlowException invalidEvaluation(String message) {
        return new AuthFlowException(ErrorCode.ALERT_EVALUATION_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    private AuthFlowException invalidRule(String message) {
        return new AuthFlowException(ErrorCode.ALERT_RULE_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    private record EvaluationOutcome(
            List<AlertRecordEntity> records,
            int evaluatedRuleCount,
            AlertCaseProcessingSummary processingSummary
    ) {
    }

    private record EvaluationValue(boolean applicable, Double numericValue, String metricCode, String displayValue) {
    }

    private record RuleHit(double rawConfidence, double finalConfidence, String metricCode, String metricValue) {
    }
}
