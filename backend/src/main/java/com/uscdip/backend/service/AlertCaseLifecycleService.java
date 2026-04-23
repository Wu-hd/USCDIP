package com.uscdip.backend.service;

import com.uscdip.backend.dto.AlertCaseProcessingSummary;
import com.uscdip.backend.dto.AlertCaseResponse;
import com.uscdip.backend.dto.AlertPolicyResponse;
import com.uscdip.backend.entity.AlertCaseEntity;
import com.uscdip.backend.entity.AlertPolicyEntity;
import com.uscdip.backend.entity.AlertRecordEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.AlertCaseRepository;
import com.uscdip.backend.repository.AlertPolicyRepository;
import com.uscdip.backend.repository.AlertRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AlertCaseLifecycleService {

    static final String CASE_STATUS_ACTIVE = "ACTIVE";
    static final String CASE_STATUS_SUPPRESSED = "SUPPRESSED";
    static final String CASE_STATUS_ESCALATED = "ESCALATED";
    static final String CASE_STATUS_REVIEW_ONLY = "REVIEW_ONLY";
    static final String CASE_STATUS_DQ_BLOCKED = "DQ_BLOCKED";
    static final String CASE_STATUS_RECOVERED = "RECOVERED";

    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final String DECISION_TRIGGERED = "TRIGGERED";
    private static final String DECISION_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    private static final String DECISION_DQ_BLOCKED = "DQ_BLOCKED";
    private static final String PROCESS_OPENED = "OPENED";
    private static final String PROCESS_DEDUPED = "DEDUPED";
    private static final String PROCESS_SUPPRESSED = "SUPPRESSED";
    private static final String PROCESS_ESCALATED = "ESCALATED";
    private static final List<String> ACTIVE_CASE_STATUSES = List.of(
            CASE_STATUS_ACTIVE,
            CASE_STATUS_SUPPRESSED,
            CASE_STATUS_ESCALATED,
            CASE_STATUS_REVIEW_ONLY,
            CASE_STATUS_DQ_BLOCKED
    );
    private static final List<String> SEVERITY_LADDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final AlertPolicyRepository alertPolicyRepository;
    private final AlertCaseRepository alertCaseRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final ObjectScopeService objectScopeService;

    public AlertCaseLifecycleService(
            AlertPolicyRepository alertPolicyRepository,
            AlertCaseRepository alertCaseRepository,
            AlertRecordRepository alertRecordRepository,
            ObjectScopeService objectScopeService
    ) {
        this.alertPolicyRepository = alertPolicyRepository;
        this.alertCaseRepository = alertCaseRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.objectScopeService = objectScopeService;
    }

    @Transactional
    public AlertCaseProcessingSummary processAlerts(List<AlertRecordEntity> records) {
        if (records == null || records.isEmpty()) {
            return AlertCaseProcessingSummary.empty();
        }

        Map<String, AlertPolicyEntity> policyMap = loadPolicies(records);
        int openedCaseCount = 0;
        int dedupedCount = 0;
        int suppressedCount = 0;
        int escalatedCount = 0;

        List<AlertRecordEntity> sortedRecords = records.stream()
                .sorted(Comparator.comparing(this::resolveTriggerTime)
                        .thenComparing(AlertRecordEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AlertRecordEntity::getAlertId))
                .toList();

        for (AlertRecordEntity record : sortedRecords) {
            AlertPolicyEntity policy = requirePolicy(policyMap, record.getRuleCode());
            validatePolicy(policy);

            AlertCaseEntity caseEntity = resolveActiveCase(record, policy);
            LocalDateTime processedAt = LocalDateTime.now();

            if (caseEntity == null) {
                caseEntity = openCase(record, policy, processedAt);
                openedCaseCount++;
                applyRecordLifecycle(record, caseEntity, PROCESS_OPENED, false, processedAt);
            } else {
                boolean suppressed = isSuppressed(caseEntity, record);
                int previousEscalationLevel = safeInt(caseEntity.getEscalationLevel());

                caseEntity.setLastAlertId(record.getAlertId());
                caseEntity.setLastTriggeredAt(resolveTriggerTime(record));
                caseEntity.setTraceId(record.getTraceId());
                caseEntity.setHitCount(safeInt(caseEntity.getHitCount()) + 1);
                caseEntity.setUpdatedAt(processedAt);

                if (suppressed) {
                    caseEntity.setCaseStatus(resolveCaseStatus(record.getDecision(), true, previousEscalationLevel));
                    applyRecordLifecycle(record, caseEntity, PROCESS_SUPPRESSED, true, processedAt);
                    suppressedCount++;
                } else {
                    caseEntity.setUnsuppressedHitCount(safeInt(caseEntity.getUnsuppressedHitCount()) + 1);
                    caseEntity.setSuppressedUntil(resolveTriggerTime(record).plusSeconds(policy.getSuppressWindowSeconds()));

                    int nextEscalationLevel = resolveEscalationLevel(caseEntity, policy, record);
                    caseEntity.setEscalationLevel(nextEscalationLevel);
                    caseEntity.setSeverityCurrent(resolveSeverity(caseEntity.getSeverityCurrent(), record.getSeverity(), nextEscalationLevel));
                    caseEntity.setCaseStatus(resolveCaseStatus(record.getDecision(), false, nextEscalationLevel));

                    boolean escalated = nextEscalationLevel > previousEscalationLevel;
                    applyRecordLifecycle(record, caseEntity, escalated ? PROCESS_ESCALATED : PROCESS_DEDUPED, false, processedAt);
                    dedupedCount++;
                    if (escalated) {
                        escalatedCount++;
                    }
                }
            }

            alertCaseRepository.save(caseEntity);
        }

        alertRecordRepository.saveAll(sortedRecords);
        return new AlertCaseProcessingSummary(openedCaseCount, dedupedCount, suppressedCount, escalatedCount);
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertCaseResponse> listCases(
            AuthorizationContext context,
            int page,
            int pageSize,
            String deviceId,
            String ruleCode,
            String caseStatus,
            String severity,
            Integer escalationLevel
    ) {
        List<AlertCaseResponse> items = filterVisibleCases(context, alertCaseRepository.findAllByOrderByUpdatedAtDescCaseIdDesc()).stream()
                .filter(item -> matches(item.getDeviceId(), deviceId))
                .filter(item -> matches(item.getRuleCode(), ruleCode))
                .filter(item -> matches(item.getCaseStatus(), caseStatus))
                .filter(item -> matches(item.getSeverityCurrent(), severity))
                .filter(item -> escalationLevel == null || Objects.equals(item.getEscalationLevel(), escalationLevel))
                .map(this::toCaseResponse)
                .toList();
        return paginate(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public AlertCaseResponse getCaseDetail(AuthorizationContext context, String caseId) {
        AlertCaseEntity caseEntity = alertCaseRepository.findById(caseId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.ALERT_CASE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Alert case not found: " + caseId
                ));
        requireCaseAccess(context, caseEntity);
        return toCaseResponse(caseEntity);
    }

    @Transactional(readOnly = true)
    public List<AlertPolicyResponse> listPolicies() {
        return alertPolicyRepository.findAllByOrderByRuleCodeAsc().stream()
                .map(this::toPolicyResponse)
                .toList();
    }

    @Transactional
    public int recoverExpiredCasesNow() {
        LocalDateTime now = LocalDateTime.now();
        List<AlertCaseEntity> activeCases = alertCaseRepository.findByCaseStatusInAndLastTriggeredAtBefore(ACTIVE_CASE_STATUSES, now);
        if (activeCases.isEmpty()) {
            return 0;
        }

        Map<String, AlertPolicyEntity> policyMap = alertPolicyRepository.findAllByOrderByRuleCodeAsc().stream()
                .collect(Collectors.toMap(policy -> normalize(policy.getRuleCode()), policy -> policy, (left, right) -> left, LinkedHashMap::new));

        int recovered = 0;
        for (AlertCaseEntity caseEntity : activeCases) {
            AlertPolicyEntity policy = policyMap.get(normalize(caseEntity.getRuleCode()));
            if (policy == null || !policy.isEnabled()) {
                continue;
            }
            try {
                validatePolicy(policy);
            } catch (AuthFlowException ex) {
                continue;
            }
            LocalDateTime recoveryCutoff = now.minusSeconds(policy.getRecoveryWindowSeconds());
            if (caseEntity.getLastTriggeredAt() != null && caseEntity.getLastTriggeredAt().isBefore(recoveryCutoff)) {
                caseEntity.setCaseStatus(CASE_STATUS_RECOVERED);
                caseEntity.setRecoveredAt(now);
                caseEntity.setUpdatedAt(now);
                alertCaseRepository.save(caseEntity);
                recovered++;
            }
        }
        return recovered;
    }

    @Scheduled(fixedDelayString = "${backend.alert.recovery-scan-fixed-delay-ms:60000}")
    @Transactional
    public void recoverExpiredCases() {
        recoverExpiredCasesNow();
    }

    private Map<String, AlertPolicyEntity> loadPolicies(Collection<AlertRecordEntity> records) {
        Set<String> ruleCodes = records.stream()
                .map(AlertRecordEntity::getRuleCode)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return alertPolicyRepository.findByRuleCodeIn(ruleCodes).stream()
                .collect(Collectors.toMap(policy -> normalize(policy.getRuleCode()), policy -> policy, (left, right) -> left, LinkedHashMap::new));
    }

    private AlertPolicyEntity requirePolicy(Map<String, AlertPolicyEntity> policyMap, String ruleCode) {
        AlertPolicyEntity policy = policyMap.get(normalize(ruleCode));
        if (policy == null || !policy.isEnabled()) {
            throw new AuthFlowException(
                    ErrorCode.ALERT_POLICY_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "Alert policy not found for rule: " + ruleCode
            );
        }
        return policy;
    }

    private void validatePolicy(AlertPolicyEntity policy) {
        if (policy.getDedupeWindowSeconds() == null || policy.getDedupeWindowSeconds() <= 0
                || policy.getSuppressWindowSeconds() == null || policy.getSuppressWindowSeconds() < 0
                || policy.getRecoveryWindowSeconds() == null || policy.getRecoveryWindowSeconds() <= 0
                || policy.getEscalateThresholdL1() == null || policy.getEscalateThresholdL1() <= 0
                || policy.getEscalateThresholdL2() == null || policy.getEscalateThresholdL2() <= 0
                || policy.getEscalateThresholdL2() < policy.getEscalateThresholdL1()) {
            throw new AuthFlowException(
                    ErrorCode.ALERT_POLICY_INVALID,
                    HttpStatus.BAD_REQUEST,
                    "Alert policy is invalid for rule: " + policy.getRuleCode()
            );
        }
    }

    private AlertCaseEntity resolveActiveCase(AlertRecordEntity record, AlertPolicyEntity policy) {
        List<AlertCaseEntity> candidates = alertCaseRepository.findByRuleCodeAndCaseStatusInOrderByLastTriggeredAtDesc(
                normalize(record.getRuleCode()),
                ACTIVE_CASE_STATUSES
        );
        LocalDateTime windowStart = resolveTriggerTime(record).minusSeconds(policy.getDedupeWindowSeconds());
        return candidates.stream()
                .filter(candidate -> matchesCaseObject(candidate, record))
                .filter(candidate -> candidate.getLastTriggeredAt() != null && !candidate.getLastTriggeredAt().isBefore(windowStart))
                .findFirst()
                .orElse(null);
    }

    private AlertCaseEntity openCase(AlertRecordEntity record, AlertPolicyEntity policy, LocalDateTime processedAt) {
        LocalDateTime triggeredAt = resolveTriggerTime(record);
        AlertCaseEntity caseEntity = new AlertCaseEntity();
        caseEntity.setCaseId("ALCASE-" + UUID.randomUUID());
        caseEntity.setDedupeKey(buildDedupeKey(record, triggeredAt));
        caseEntity.setDeviceId(record.getDeviceId());
        caseEntity.setSegmentId(record.getSegmentId());
        caseEntity.setNodeId(record.getNodeId());
        caseEntity.setRuleCode(normalize(record.getRuleCode()));
        caseEntity.setSeverityCurrent(normalize(record.getSeverity()));
        caseEntity.setDecisionSnapshot(normalize(record.getDecision()));
        caseEntity.setCaseStatus(resolveCaseStatus(record.getDecision(), false, 0));
        caseEntity.setFirstAlertId(record.getAlertId());
        caseEntity.setLastAlertId(record.getAlertId());
        caseEntity.setFirstTriggeredAt(triggeredAt);
        caseEntity.setLastTriggeredAt(triggeredAt);
        caseEntity.setHitCount(1);
        caseEntity.setUnsuppressedHitCount(1);
        caseEntity.setEscalationLevel(0);
        caseEntity.setSuppressedUntil(triggeredAt.plusSeconds(policy.getSuppressWindowSeconds()));
        caseEntity.setRecoveredAt(null);
        caseEntity.setTraceId(record.getTraceId());
        caseEntity.setCreatedAt(processedAt);
        caseEntity.setUpdatedAt(processedAt);
        return caseEntity;
    }

    private boolean matchesCaseObject(AlertCaseEntity caseEntity, AlertRecordEntity record) {
        if (record.getDeviceId() != null && !record.getDeviceId().isBlank()) {
            return normalize(record.getDeviceId()).equals(normalize(caseEntity.getDeviceId()));
        }
        return normalize(record.getSegmentId()).equals(normalize(caseEntity.getSegmentId()))
                && normalize(record.getNodeId()).equals(normalize(caseEntity.getNodeId()));
    }

    private boolean isSuppressed(AlertCaseEntity caseEntity, AlertRecordEntity record) {
        LocalDateTime suppressedUntil = caseEntity.getSuppressedUntil();
        if (suppressedUntil == null) {
            return false;
        }
        LocalDateTime triggerTime = resolveTriggerTime(record);
        return !triggerTime.isAfter(suppressedUntil);
    }

    private int resolveEscalationLevel(AlertCaseEntity caseEntity, AlertPolicyEntity policy, AlertRecordEntity record) {
        if (!DECISION_TRIGGERED.equals(normalize(record.getDecision()))) {
            return safeInt(caseEntity.getEscalationLevel());
        }
        int[] thresholds = adjustedThresholds(policy, record.getDqScoreSnapshot());
        int unsuppressedHitCount = safeInt(caseEntity.getUnsuppressedHitCount());
        int computedLevel = 0;
        if (unsuppressedHitCount >= thresholds[1]) {
            computedLevel = 2;
        } else if (unsuppressedHitCount >= thresholds[0]) {
            computedLevel = 1;
        }
        return Math.max(computedLevel, safeInt(caseEntity.getEscalationLevel()));
    }

    private int[] adjustedThresholds(AlertPolicyEntity policy, Double dqScore) {
        int l1 = policy.getEscalateThresholdL1();
        int l2 = policy.getEscalateThresholdL2();
        double safeScore = dqScore == null ? 0.0d : dqScore;
        if (safeScore >= 70.0d && safeScore < 85.0d) {
            l1 += 2;
            l2 += 2;
        }
        return new int[]{l1, l2};
    }

    private String resolveCaseStatus(String decision, boolean suppressed, int escalationLevel) {
        String normalizedDecision = normalize(decision);
        if (DECISION_REVIEW_REQUIRED.equals(normalizedDecision)) {
            return CASE_STATUS_REVIEW_ONLY;
        }
        if (DECISION_DQ_BLOCKED.equals(normalizedDecision)) {
            return CASE_STATUS_DQ_BLOCKED;
        }
        if (escalationLevel > 0) {
            return CASE_STATUS_ESCALATED;
        }
        if (suppressed) {
            return CASE_STATUS_SUPPRESSED;
        }
        return CASE_STATUS_ACTIVE;
    }

    private String resolveSeverity(String currentSeverity, String incomingSeverity, int escalationLevel) {
        String baselineSeverity = normalize(incomingSeverity == null ? currentSeverity : incomingSeverity);
        int baseIndex = SEVERITY_LADDER.indexOf(baselineSeverity);
        int safeBaseIndex = baseIndex < 0 ? 0 : baseIndex;
        int targetIndex = Math.min(safeBaseIndex + escalationLevel, SEVERITY_LADDER.size() - 1);
        return SEVERITY_LADDER.get(targetIndex);
    }

    private void applyRecordLifecycle(
            AlertRecordEntity record,
            AlertCaseEntity caseEntity,
            String processStatus,
            boolean suppressed,
            LocalDateTime processedAt
    ) {
        record.setCaseId(caseEntity.getCaseId());
        record.setDedupeKey(caseEntity.getDedupeKey());
        record.setProcessStatus(processStatus);
        record.setSuppressed(suppressed);
        record.setEscalationLevel(caseEntity.getEscalationLevel());
        record.setProcessedAt(processedAt);
    }

    private String buildDedupeKey(AlertRecordEntity record, LocalDateTime windowStart) {
        String objectKey = record.getDeviceId() != null && !record.getDeviceId().isBlank()
                ? "DEVICE:" + normalize(record.getDeviceId())
                : "SEGMENT:" + normalize(record.getSegmentId()) + "|NODE:" + normalize(record.getNodeId());
        return objectKey + "|RULE:" + normalize(record.getRuleCode()) + "|WINDOW:" + windowStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private LocalDateTime resolveTriggerTime(AlertRecordEntity record) {
        return record.getEventTime() != null ? record.getEventTime() : record.getCreatedAt();
    }

    private List<AlertCaseEntity> filterVisibleCases(AuthorizationContext context, List<AlertCaseEntity> cases) {
        if (cases.isEmpty()) {
            return List.of();
        }
        if (context.platformAdmin()) {
            return cases;
        }
        Set<String> accessibleDeviceIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_DEVICE,
                cases.stream()
                        .map(AlertCaseEntity::getDeviceId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                MENU_ASSET_READ
        );
        return cases.stream()
                .filter(caseEntity -> caseEntity.getDeviceId() != null && accessibleDeviceIds.contains(caseEntity.getDeviceId()))
                .toList();
    }

    private void requireCaseAccess(AuthorizationContext context, AlertCaseEntity caseEntity) {
        if (caseEntity.getDeviceId() == null || caseEntity.getDeviceId().isBlank()) {
            if (!context.platformAdmin()) {
                throw new AuthFlowException(ErrorCode.DATA_SCOPE_DENIED, HttpStatus.FORBIDDEN, ErrorCode.DATA_SCOPE_DENIED.defaultMessage());
            }
            return;
        }
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, caseEntity.getDeviceId(), MENU_ASSET_READ);
    }

    private PageResponse<AlertCaseResponse> paginate(List<AlertCaseResponse> items, int page, int pageSize) {
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

    private AlertCaseResponse toCaseResponse(AlertCaseEntity entity) {
        return new AlertCaseResponse(
                entity.getCaseId(),
                entity.getDedupeKey(),
                entity.getDeviceId(),
                entity.getSegmentId(),
                entity.getNodeId(),
                entity.getRuleCode(),
                entity.getSeverityCurrent(),
                entity.getDecisionSnapshot(),
                entity.getCaseStatus(),
                entity.getFirstAlertId(),
                entity.getLastAlertId(),
                entity.getFirstTriggeredAt(),
                entity.getLastTriggeredAt(),
                entity.getHitCount(),
                entity.getUnsuppressedHitCount(),
                entity.getEscalationLevel(),
                entity.getSuppressedUntil(),
                entity.getRecoveredAt(),
                entity.getTraceId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private AlertPolicyResponse toPolicyResponse(AlertPolicyEntity entity) {
        return new AlertPolicyResponse(
                entity.getPolicyId(),
                entity.getRuleCode(),
                entity.getDedupeWindowSeconds(),
                entity.getSuppressWindowSeconds(),
                entity.getRecoveryWindowSeconds(),
                entity.getEscalateThresholdL1(),
                entity.getEscalateThresholdL2(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
