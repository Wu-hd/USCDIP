package com.uscdip.backend.service;

import com.uscdip.backend.dto.DataQualityBatchSummary;
import com.uscdip.backend.dto.DataQualityScoreResponse;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.TsMetricRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataQualityScoringService {

    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final String FLAG_VALIDITY_RANGE_VIOLATION = "VALIDITY_RANGE_VIOLATION";
    private static final String FLAG_VALIDITY_PROFILE_MISSING = "VALIDITY_PROFILE_MISSING";
    private static final String FLAG_TIMELINESS_DELAYED = "TIMELINESS_DELAYED";
    private static final String FLAG_BACKFILL_DATA = "BACKFILL_DATA";
    private static final String FLAG_CONSISTENCY_BASELINE_MISSING = "CONSISTENCY_BASELINE_MISSING";
    private static final String FLAG_CONSISTENCY_DRIFT = "CONSISTENCY_DRIFT";
    private static final String FLAG_STABILITY_WINDOW_MISSING = "STABILITY_WINDOW_MISSING";
    private static final String FLAG_STABILITY_FLATLINE = "STABILITY_FLATLINE";
    private static final String FLAG_STABILITY_SPIKE = "STABILITY_SPIKE";
    private static final Map<String, MetricProfile> METRIC_PROFILES = Map.of(
            "PRESSURE", new MetricProfile(0.0, 2.0, 0.03, 0.5),
            "TEMPERATURE", new MetricProfile(-40.0, 120.0, 0.5, 15.0),
            "VIBRATION", new MetricProfile(0.0, 5.0, 0.02, 1.0),
            "40001", new MetricProfile(0.0, 500.0, 1.0, 100.0)
    );

    private final TsMetricRepository tsMetricRepository;
    private final ObjectScopeService objectScopeService;

    public DataQualityScoringService(TsMetricRepository tsMetricRepository, ObjectScopeService objectScopeService) {
        this.tsMetricRepository = tsMetricRepository;
        this.objectScopeService = objectScopeService;
    }

    @Transactional
    public void scoreBatch(String sourceBatchId) {
        List<TsMetricEntity> metrics = tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(sourceBatchId);
        if (metrics.isEmpty()) {
            return;
        }
        metrics.forEach(this::scoreMetric);
        tsMetricRepository.saveAll(metrics);
    }

    @Transactional(readOnly = true)
    public DataQualityBatchSummary summarizeBatch(String sourceBatchId) {
        List<TsMetricEntity> metrics = tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(sourceBatchId);
        if (metrics.isEmpty()) {
            return null;
        }
        List<Double> scores = metrics.stream()
                .map(TsMetricEntity::getDqScore)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (scores.isEmpty()) {
            return null;
        }
        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        Map<String, Long> breakdown = metrics.stream()
                .map(TsMetricEntity::getDqLevel)
                .filter(level -> level != null && !level.isBlank())
                .collect(Collectors.groupingBy(level -> level, LinkedHashMap::new, Collectors.counting()));
        for (String level : List.of("A", "B", "C", "D")) {
            breakdown.putIfAbsent(level, 0L);
        }
        return new DataQualityBatchSummary(round(avg), round(min), round(max), breakdown);
    }

    @Transactional(readOnly = true)
    public PageResponse<DataQualityScoreResponse> listScores(
            AuthorizationContext context,
            int page,
            int pageSize,
            String deviceId,
            String metricCode,
            String dqLevel,
            Double minScore,
            Double maxScore,
            String sourceBatchId,
            Boolean isBackfill,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        validateQuery(dqLevel, minScore, maxScore, startTime, endTime);
        List<TsMetricEntity> metrics = filterVisibleMetrics(context, tsMetricRepository.findAllByOrderByDqScoredAtDescEventTimeDesc());
        List<DataQualityScoreResponse> items = metrics.stream()
                .filter(metric -> matches(metric, deviceId, metricCode, dqLevel, minScore, maxScore, sourceBatchId, isBackfill, startTime, endTime))
                .map(this::toResponse)
                .toList();
        return paginate(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public DataQualityScoreResponse getScoreDetail(AuthorizationContext context, String sourceRecordId) {
        TsMetricEntity metric = tsMetricRepository.findById(sourceRecordId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.DQ_SCORE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Data quality score not found: " + sourceRecordId
                ));
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_DEVICE, metric.getDeviceId(), MENU_ASSET_READ);
        return toResponse(metric);
    }

    private void scoreMetric(TsMetricEntity metric) {
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        double completeness = scoreCompleteness(metric);
        double validity = scoreValidity(metric, flags);
        double timeliness = scoreTimeliness(metric, flags);
        double consistency = scoreConsistency(metric, flags);
        double stability = scoreStability(metric, flags);
        double score = round(100.0 * ((0.30 * completeness) + (0.25 * validity) + (0.20 * timeliness) + (0.15 * consistency) + (0.10 * stability)));

        metric.setDqCompleteness(completeness);
        metric.setDqValidity(validity);
        metric.setDqTimeliness(timeliness);
        metric.setDqConsistency(consistency);
        metric.setDqStability(stability);
        metric.setDqScore(score);
        metric.setDqLevel(resolveLevel(score));
        metric.setDqFlags(flags.isEmpty() ? null : String.join(",", flags));
        metric.setDqAlarmConfFactor(round(0.5 + (0.5 * score / 100.0)));
        metric.setDqScoredAt(LocalDateTime.now());
    }

    private double scoreCompleteness(TsMetricEntity metric) {
        return hasText(metric.getDeviceId())
                && hasText(metric.getMetricCode())
                && hasText(metric.getMetricValue())
                && metric.getEventTime() != null
                && metric.getRecvTime() != null
                && metric.getDeviceTime() != null
                && hasText(metric.getTraceId())
                ? 1.0
                : 0.0;
    }

    private double scoreValidity(TsMetricEntity metric, Set<String> flags) {
        Double numericValue = parseNumeric(metric.getMetricValue());
        if (numericValue == null) {
            flags.add(FLAG_VALIDITY_RANGE_VIOLATION);
            return 0.0;
        }
        MetricProfile profile = resolveProfile(metric.getMetricCode());
        if (profile == null) {
            flags.add(FLAG_VALIDITY_PROFILE_MISSING);
            return 0.5;
        }
        if (numericValue < profile.min() || numericValue > profile.max()) {
            flags.add(FLAG_VALIDITY_RANGE_VIOLATION);
            return 0.2;
        }
        return 1.0;
    }

    private double scoreTimeliness(TsMetricEntity metric, Set<String> flags) {
        long latencyMs = metric.getLatencyMs() == null ? 0L : metric.getLatencyMs();
        if (metric.isBackfill()) {
            flags.add(FLAG_BACKFILL_DATA);
        }
        if (latencyMs <= 5_000L) {
            return 1.0;
        }
        if (latencyMs <= 30_000L) {
            return 0.8;
        }
        flags.add(FLAG_TIMELINESS_DELAYED);
        if (latencyMs <= 300_000L) {
            return 0.5;
        }
        return 0.2;
    }

    private double scoreConsistency(TsMetricEntity metric, Set<String> flags) {
        Optional<TsMetricEntity> previousOptional = tsMetricRepository
                .findTopByDeviceIdAndMetricCodeAndEventTimeBeforeOrderByEventTimeDesc(
                        metric.getDeviceId(),
                        metric.getMetricCode(),
                        metric.getEventTime()
                );
        if (previousOptional.isEmpty()) {
            flags.add(FLAG_CONSISTENCY_BASELINE_MISSING);
            return 0.5;
        }
        Double currentValue = parseNumeric(metric.getMetricValue());
        Double previousValue = parseNumeric(previousOptional.get().getMetricValue());
        if (currentValue == null || previousValue == null) {
            flags.add(FLAG_CONSISTENCY_BASELINE_MISSING);
            return 0.5;
        }
        double denominator = Math.max(Math.abs(previousValue), 1.0);
        double relativeChange = Math.abs(currentValue - previousValue) / denominator;
        if (relativeChange <= 0.15) {
            return 1.0;
        }
        if (relativeChange <= 0.50) {
            return 0.8;
        }
        flags.add(FLAG_CONSISTENCY_DRIFT);
        if (relativeChange <= 1.0) {
            return 0.5;
        }
        return 0.2;
    }

    private double scoreStability(TsMetricEntity metric, Set<String> flags) {
        List<TsMetricEntity> previous = tsMetricRepository.findTop4ByDeviceIdAndMetricCodeAndEventTimeBeforeOrderByEventTimeDesc(
                metric.getDeviceId(),
                metric.getMetricCode(),
                metric.getEventTime()
        );
        if (previous.size() < 4) {
            flags.add(FLAG_STABILITY_WINDOW_MISSING);
            return 0.5;
        }
        MetricProfile profile = resolveProfile(metric.getMetricCode());
        Double currentValue = parseNumeric(metric.getMetricValue());
        List<Double> window = previous.stream()
                .map(TsMetricEntity::getMetricValue)
                .map(this::parseNumeric)
                .collect(Collectors.toList());
        if (currentValue == null || window.stream().anyMatch(java.util.Objects::isNull)) {
            return 0.5;
        }
        window.add(currentValue);
        double max = window.stream().mapToDouble(Double::doubleValue).max().orElse(currentValue);
        double min = window.stream().mapToDouble(Double::doubleValue).min().orElse(currentValue);
        double epsilon = profile == null ? 0.0001 : profile.flatlineEpsilon();
        if ((max - min) <= epsilon) {
            flags.add(FLAG_STABILITY_FLATLINE);
            return 0.2;
        }
        Double previousValue = parseNumeric(previous.get(0).getMetricValue());
        if (previousValue != null) {
            double delta = Math.abs(currentValue - previousValue);
            double threshold = profile == null ? Math.max(Math.abs(previousValue), 1.0) : profile.spikeThreshold();
            if (delta >= threshold) {
                flags.add(FLAG_STABILITY_SPIKE);
                return 0.3;
            }
        }
        return 1.0;
    }

    private List<TsMetricEntity> filterVisibleMetrics(AuthorizationContext context, Collection<TsMetricEntity> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return List.of();
        }
        Set<String> accessibleDeviceIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_DEVICE,
                metrics.stream().map(TsMetricEntity::getDeviceId).collect(Collectors.toCollection(LinkedHashSet::new)),
                MENU_ASSET_READ
        );
        return metrics.stream()
                .filter(metric -> accessibleDeviceIds.contains(metric.getDeviceId()))
                .sorted(Comparator.comparing(TsMetricEntity::getDqScoredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TsMetricEntity::getEventTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TsMetricEntity::getSourceRecordId, Comparator.reverseOrder()))
                .toList();
    }

    private boolean matches(
            TsMetricEntity metric,
            String deviceId,
            String metricCode,
            String dqLevel,
            Double minScore,
            Double maxScore,
            String sourceBatchId,
            Boolean isBackfill,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (hasText(deviceId) && !deviceId.equalsIgnoreCase(metric.getDeviceId())) {
            return false;
        }
        if (hasText(metricCode) && !metricCode.equalsIgnoreCase(metric.getMetricCode())) {
            return false;
        }
        if (hasText(dqLevel) && !dqLevel.equalsIgnoreCase(metric.getDqLevel())) {
            return false;
        }
        if (minScore != null && (metric.getDqScore() == null || metric.getDqScore() < minScore)) {
            return false;
        }
        if (maxScore != null && (metric.getDqScore() == null || metric.getDqScore() > maxScore)) {
            return false;
        }
        if (hasText(sourceBatchId) && !sourceBatchId.equalsIgnoreCase(metric.getSourceBatchId())) {
            return false;
        }
        if (isBackfill != null && metric.isBackfill() != isBackfill) {
            return false;
        }
        if (startTime != null && (metric.getEventTime() == null || metric.getEventTime().isBefore(startTime))) {
            return false;
        }
        return endTime == null || (metric.getEventTime() != null && !metric.getEventTime().isAfter(endTime));
    }

    private void validateQuery(String dqLevel, Double minScore, Double maxScore, LocalDateTime startTime, LocalDateTime endTime) {
        if (hasText(dqLevel) && !Set.of("A", "B", "C", "D").contains(dqLevel.trim().toUpperCase())) {
            throw new AuthFlowException(ErrorCode.DQ_QUERY_INVALID, HttpStatus.BAD_REQUEST, "dqLevel must be one of A/B/C/D");
        }
        if (minScore != null && (minScore < 0 || minScore > 100)) {
            throw new AuthFlowException(ErrorCode.DQ_QUERY_INVALID, HttpStatus.BAD_REQUEST, "minScore must be between 0 and 100");
        }
        if (maxScore != null && (maxScore < 0 || maxScore > 100)) {
            throw new AuthFlowException(ErrorCode.DQ_QUERY_INVALID, HttpStatus.BAD_REQUEST, "maxScore must be between 0 and 100");
        }
        if (minScore != null && maxScore != null && minScore > maxScore) {
            throw new AuthFlowException(ErrorCode.DQ_QUERY_INVALID, HttpStatus.BAD_REQUEST, "minScore must not be greater than maxScore");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new AuthFlowException(ErrorCode.DQ_QUERY_INVALID, HttpStatus.BAD_REQUEST, "startTime must not be later than endTime");
        }
    }

    private DataQualityScoreResponse toResponse(TsMetricEntity metric) {
        return new DataQualityScoreResponse(
                metric.getSourceRecordId(),
                metric.getSourceBatchId(),
                metric.getDeviceId(),
                metric.getMetricCode(),
                metric.getMetricValue(),
                metric.getEventTime(),
                metric.getRecvTime(),
                metric.getDeviceTime(),
                metric.isBackfill(),
                metric.getDqScore(),
                metric.getDqLevel(),
                metric.getDqFlags(),
                metric.getDqCompleteness(),
                metric.getDqValidity(),
                metric.getDqTimeliness(),
                metric.getDqConsistency(),
                metric.getDqStability(),
                metric.getDqAlarmConfFactor(),
                metric.getDqScoredAt()
        );
    }

    private PageResponse<DataQualityScoreResponse> paginate(List<DataQualityScoreResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private MetricProfile resolveProfile(String metricCode) {
        MetricProfile profile = METRIC_PROFILES.get(metricCode == null ? "" : metricCode.trim().toUpperCase());
        if (profile != null && profile.min() > profile.max()) {
            throw new AuthFlowException(ErrorCode.DQ_PROFILE_INVALID, HttpStatus.INTERNAL_SERVER_ERROR, "Invalid data quality metric profile: " + metricCode);
        }
        return profile;
    }

    private Double parseNumeric(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveLevel(double score) {
        if (score >= 85.0) {
            return "A";
        }
        if (score >= 70.0) {
            return "B";
        }
        if (score >= 60.0) {
            return "C";
        }
        return "D";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record MetricProfile(double min, double max, double flatlineEpsilon, double spikeThreshold) {
    }
}
