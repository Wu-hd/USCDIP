package com.uscdip.backend.service;

import com.uscdip.backend.dto.TsdbWriteLogResponse;
import com.uscdip.backend.dto.TsdbWriteSummary;
import com.uscdip.backend.entity.IngestBatchEntity;
import com.uscdip.backend.entity.IngestRecordEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.entity.TsWriteLogEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.IngestBatchRepository;
import com.uscdip.backend.repository.IngestRecordRepository;
import com.uscdip.backend.repository.TsMetricRepository;
import com.uscdip.backend.repository.TsWriteLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TsdbWriteService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SUCCESS = "SUCCESS";
    static final String STATUS_FAILED = "FAILED";
    static final String STATUS_RETRYING = "RETRYING";
    static final String WRITE_TYPE_ONLINE = "ONLINE";
    static final String WRITE_TYPE_BACKFILL = "BACKFILL";
    private static final String MENU_ASSET_READ = "MENU:ASSET:READ";
    private static final int ERROR_MAX_LENGTH = 2000;

    private final IngestBatchRepository ingestBatchRepository;
    private final IngestRecordRepository ingestRecordRepository;
    private final TsMetricRepository tsMetricRepository;
    private final TsWriteLogRepository tsWriteLogRepository;
    private final DataQualityScoringService dataQualityScoringService;
    private final ObjectScopeService objectScopeService;
    private final int maxAttempts;
    private final int retryBaseDelaySeconds;

    public TsdbWriteService(
            IngestBatchRepository ingestBatchRepository,
            IngestRecordRepository ingestRecordRepository,
            TsMetricRepository tsMetricRepository,
            TsWriteLogRepository tsWriteLogRepository,
            DataQualityScoringService dataQualityScoringService,
            ObjectScopeService objectScopeService,
            @Value("${backend.tsdb.max-attempts:3}") int maxAttempts,
            @Value("${backend.tsdb.retry-base-delay-seconds:30}") int retryBaseDelaySeconds
    ) {
        this.ingestBatchRepository = ingestBatchRepository;
        this.ingestRecordRepository = ingestRecordRepository;
        this.tsMetricRepository = tsMetricRepository;
        this.tsWriteLogRepository = tsWriteLogRepository;
        this.dataQualityScoringService = dataQualityScoringService;
        this.objectScopeService = objectScopeService;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    @Transactional
    public TsdbWriteSummary writeBatch(String batchId) {
        return executeAttempt(batchId, false, null, false).summary();
    }

    @Transactional(noRollbackFor = AuthFlowException.class)
    public TsdbWriteLogResponse retryWriteLog(AuthorizationContext context, String writeLogId, boolean failOnWriteError) {
        TsWriteLogEntity previous = tsWriteLogRepository.findById(writeLogId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.TSDB_WRITE_LOG_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "TSDB write log not found: " + writeLogId
                ));
        requireBatchAccess(context, previous.getSourceBatchId());
        AttemptResult result = executeAttempt(previous.getSourceBatchId(), true, previous, failOnWriteError);
        return toLogResponse(result.log());
    }

    @Transactional(readOnly = true)
    public PageResponse<TsdbWriteLogResponse> listWriteLogs(AuthorizationContext context, int page, int pageSize) {
        List<TsWriteLogEntity> logs = tsWriteLogRepository.findAllByOrderByCreatedAtDesc();
        Map<String, List<IngestRecordEntity>> visibleRecords = loadVisibleRecords(context, logs.stream()
                .map(TsWriteLogEntity::getSourceBatchId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<TsdbWriteLogResponse> items = logs.stream()
                .filter(log -> !visibleRecords.getOrDefault(log.getSourceBatchId(), List.of()).isEmpty())
                .map(this::toLogResponse)
                .toList();
        return paginate(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public TsdbWriteLogResponse getWriteLogDetail(AuthorizationContext context, String writeLogId) {
        TsWriteLogEntity log = tsWriteLogRepository.findById(writeLogId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.TSDB_WRITE_LOG_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "TSDB write log not found: " + writeLogId
                ));
        requireBatchAccess(context, log.getSourceBatchId());
        return toLogResponse(log);
    }

    @Transactional
    public int retryDueWrites() {
        List<TsWriteLogEntity> dueLogs = tsWriteLogRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                List.of(STATUS_FAILED, STATUS_RETRYING),
                LocalDateTime.now()
        );
        int processed = 0;
        for (TsWriteLogEntity dueLog : dueLogs) {
            IngestBatchEntity batch = ingestBatchRepository.findById(dueLog.getSourceBatchId()).orElse(null);
            if (batch == null || !dueLog.getWriteLogId().equals(batch.getLastWriteLogId())) {
                continue;
            }
            if (dueLog.getAttemptNo() != null && dueLog.getAttemptNo() >= maxAttempts) {
                continue;
            }
            executeAttempt(dueLog.getSourceBatchId(), true, dueLog, false);
            processed++;
        }
        return processed;
    }

    @Transactional(readOnly = true)
    public TsdbWriteSummary getSummaryForBatch(IngestBatchEntity batch) {
        if (batch == null || batch.getLastWriteLogId() == null || batch.getLastWriteLogId().isBlank()) {
            return batch == null || batch.getTsdbWriteStatus() == null
                    ? null
                    : new TsdbWriteSummary(batch.getTsdbWriteStatus(), null, 0, 0, 0, null);
        }
        TsWriteLogEntity log = tsWriteLogRepository.findById(batch.getLastWriteLogId()).orElse(null);
        return log == null
                ? new TsdbWriteSummary(batch.getTsdbWriteStatus(), batch.getLastWriteLogId(), 0, 0, 0, null)
                : toSummary(log);
    }

    private AttemptResult executeAttempt(
            String batchId,
            boolean retry,
            TsWriteLogEntity previousLog,
            boolean failOnWriteError
    ) {
        IngestBatchEntity batch = ingestBatchRepository.findById(batchId)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.INGEST_BATCH_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Ingest batch not found: " + batchId
                ));
        List<IngestRecordEntity> records = ingestRecordRepository.findByBatchIdOrderByEventTimeAsc(batchId);
        int attemptNo = previousLog == null ? 1 : previousLog.getAttemptNo() + 1;
        String initialStatus = retry ? STATUS_RETRYING : STATUS_PENDING;
        LocalDateTime now = LocalDateTime.now();
        TsWriteLogEntity log = new TsWriteLogEntity(
                "TSWL-" + UUID.randomUUID(),
                batchId,
                batch.isBackfill() ? WRITE_TYPE_BACKFILL : WRITE_TYPE_ONLINE,
                attemptNo,
                initialStatus,
                records.size(),
                0,
                records.size(),
                null,
                batch.getTraceId(),
                null,
                null,
                now,
                now
        );
        tsWriteLogRepository.save(log);

        batch.setTsdbWriteStatus(initialStatus);
        batch.setLastWriteLogId(log.getWriteLogId());
        batch.setLastWriteAt(now);
        ingestBatchRepository.save(batch);

        long startedAt = System.currentTimeMillis();
        try {
            writeMetrics(batch, records);
            dataQualityScoringService.scoreBatch(batchId);
            long durationMs = System.currentTimeMillis() - startedAt;
            log.setStatus(STATUS_SUCCESS);
            log.setSuccessCount(records.size());
            log.setFailedCount(0);
            log.setDurationMs(durationMs);
            log.setNextRetryAt(null);
            log.setLastError(null);
            log.setUpdatedAt(LocalDateTime.now());
            tsWriteLogRepository.save(log);

            batch.setTsdbWriteStatus(STATUS_SUCCESS);
            batch.setLastWriteLogId(log.getWriteLogId());
            batch.setLastWriteAt(LocalDateTime.now());
            ingestBatchRepository.save(batch);
            return new AttemptResult(log, toSummary(log));
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.setStatus(STATUS_FAILED);
            log.setSuccessCount(0);
            log.setFailedCount(records.size());
            log.setDurationMs(durationMs);
            log.setLastError(abbreviateError(ex.getMessage()));
            log.setNextRetryAt(shouldAutoRetry(attemptNo) ? computeNextRetryAt(attemptNo) : null);
            log.setUpdatedAt(LocalDateTime.now());
            tsWriteLogRepository.save(log);

            batch.setTsdbWriteStatus(STATUS_FAILED);
            batch.setLastWriteLogId(log.getWriteLogId());
            batch.setLastWriteAt(LocalDateTime.now());
            ingestBatchRepository.save(batch);

            if (failOnWriteError) {
                throw new AuthFlowException(
                        ErrorCode.TSDB_WRITE_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "TSDB write failed for batch: " + batchId
                );
            }
            return new AttemptResult(log, toSummary(log));
        }
    }

    private void writeMetrics(IngestBatchEntity batch, List<IngestRecordEntity> records) {
        List<TsMetricEntity> toInsert = records.stream()
                .filter(record -> shouldInsertMetric(batch, record))
                .map(record -> toTsMetric(batch, record))
                .toList();
        if (!toInsert.isEmpty()) {
            tsMetricRepository.saveAll(toInsert);
        }
    }

    private boolean shouldInsertMetric(IngestBatchEntity batch, IngestRecordEntity record) {
        if (tsMetricRepository.existsById(record.getRecordId())) {
            return false;
        }
        if (!batch.isBackfill()) {
            return true;
        }
        if (batch.getBatchNo() == null || batch.getSeqNo() == null) {
            throw new AuthFlowException(
                    ErrorCode.BACKFILL_PAYLOAD_INVALID,
                    HttpStatus.BAD_REQUEST,
                    "Backfill batch metadata is incomplete for " + batch.getBatchId()
            );
        }
        Optional<TsMetricEntity> existing = tsMetricRepository.findByDeviceIdAndMetricCodeAndBatchNoAndSeqNo(
                record.getDeviceId(),
                record.getMetricCode(),
                batch.getBatchNo(),
                batch.getSeqNo()
        );
        if (existing.isEmpty()) {
            return true;
        }
        TsMetricEntity current = existing.get();
        if (sameBackfillMetric(current, batch, record)) {
            return false;
        }
        throw new AuthFlowException(
                ErrorCode.BACKFILL_DUPLICATE,
                HttpStatus.CONFLICT,
                "Backfill metric already exists with different payload for "
                        + record.getDeviceId() + ":" + record.getMetricCode()
                        + " batchNo=" + batch.getBatchNo() + " seqNo=" + batch.getSeqNo()
        );
    }

    private boolean sameBackfillMetric(TsMetricEntity existing, IngestBatchEntity batch, IngestRecordEntity record) {
        return safeEquals(existing.getMetricValue(), record.getMetricValue())
                && safeEquals(existing.getEventTime(), record.getEventTime())
                && safeEquals(existing.getRecvTime(), record.getRecvTime())
                && safeEquals(existing.getDeviceTime(), record.getDeviceTime())
                && safeEquals(existing.getOriginalSampleTime(), batch.getOriginalSampleTime())
                && safeEquals(existing.getTraceId(), record.getTraceId());
    }

    private TsMetricEntity toTsMetric(IngestBatchEntity batch, IngestRecordEntity record) {
        return new TsMetricEntity(
                record.getRecordId(),
                record.getDeviceId(),
                record.getMetricCode(),
                record.getMetricValue(),
                record.getEventTime(),
                record.getRecvTime(),
                record.getDeviceTime(),
                record.getTraceId(),
                record.isBackfill(),
                batch.getBatchId(),
                batch.getBatchNo(),
                batch.getSeqNo(),
                batch.getOriginalSampleTime(),
                batch.isBackfill(),
                computeLatencyMs(record),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private long computeLatencyMs(IngestRecordEntity record) {
        if (record.getEventTime() == null || record.getRecvTime() == null || record.getRecvTime().isBefore(record.getEventTime())) {
            return 0L;
        }
        return Duration.between(record.getEventTime(), record.getRecvTime()).toMillis();
    }

    private LocalDateTime computeNextRetryAt(int attemptNo) {
        long delaySeconds = (long) retryBaseDelaySeconds * (1L << Math.max(0, attemptNo - 1));
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

    private boolean shouldAutoRetry(int attemptNo) {
        return attemptNo < maxAttempts;
    }

    private void requireBatchAccess(AuthorizationContext context, String batchId) {
        List<IngestRecordEntity> records = ingestRecordRepository.findByBatchIdOrderByEventTimeAsc(batchId);
        Set<String> accessibleDeviceIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_DEVICE,
                records.stream().map(IngestRecordEntity::getDeviceId).collect(Collectors.toCollection(LinkedHashSet::new)),
                MENU_ASSET_READ
        );
        boolean allowed = records.stream().anyMatch(record -> accessibleDeviceIds.contains(record.getDeviceId()));
        if (!allowed) {
            throw new AuthFlowException(ErrorCode.DATA_SCOPE_DENIED, HttpStatus.FORBIDDEN, ErrorCode.DATA_SCOPE_DENIED.defaultMessage());
        }
    }

    private Map<String, List<IngestRecordEntity>> loadVisibleRecords(AuthorizationContext context, Collection<String> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Map.of();
        }
        List<IngestRecordEntity> records = ingestRecordRepository.findByBatchIdIn(batchIds);
        if (records.isEmpty()) {
            return Map.of();
        }
        Set<String> accessibleDeviceIds = objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_DEVICE,
                records.stream().map(IngestRecordEntity::getDeviceId).collect(Collectors.toCollection(LinkedHashSet::new)),
                MENU_ASSET_READ
        );
        return records.stream()
                .filter(record -> accessibleDeviceIds.contains(record.getDeviceId()))
                .sorted(Comparator.comparing(IngestRecordEntity::getEventTime).thenComparing(IngestRecordEntity::getRecordId))
                .collect(Collectors.groupingBy(IngestRecordEntity::getBatchId, LinkedHashMap::new, Collectors.toList()));
    }

    private PageResponse<TsdbWriteLogResponse> paginate(List<TsdbWriteLogResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private TsdbWriteLogResponse toLogResponse(TsWriteLogEntity log) {
        return new TsdbWriteLogResponse(
                log.getWriteLogId(),
                log.getSourceBatchId(),
                log.getWriteType(),
                log.getAttemptNo() == null ? 0 : log.getAttemptNo(),
                log.getStatus(),
                log.getRequestedCount() == null ? 0 : log.getRequestedCount(),
                log.getSuccessCount() == null ? 0 : log.getSuccessCount(),
                log.getFailedCount() == null ? 0 : log.getFailedCount(),
                log.getDurationMs(),
                log.getTraceId(),
                log.getLastError(),
                log.getNextRetryAt(),
                log.getCreatedAt(),
                log.getUpdatedAt()
        );
    }

    private TsdbWriteSummary toSummary(TsWriteLogEntity log) {
        return new TsdbWriteSummary(
                log.getStatus(),
                log.getWriteLogId(),
                log.getRequestedCount() == null ? 0 : log.getRequestedCount(),
                log.getSuccessCount() == null ? 0 : log.getSuccessCount(),
                log.getFailedCount() == null ? 0 : log.getFailedCount(),
                log.getDurationMs()
        );
    }

    private boolean safeEquals(Object left, Object right) {
        return java.util.Objects.equals(left, right);
    }

    private String abbreviateError(String message) {
        if (message == null || message.isBlank()) {
            return "TSDB write failed";
        }
        return message.length() <= ERROR_MAX_LENGTH ? message : message.substring(0, ERROR_MAX_LENGTH);
    }

    private record AttemptResult(TsWriteLogEntity log, TsdbWriteSummary summary) {
    }
}
