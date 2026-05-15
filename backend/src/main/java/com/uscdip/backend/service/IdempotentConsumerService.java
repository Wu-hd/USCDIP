package com.uscdip.backend.service;

import com.uscdip.backend.entity.DeadLetterEntity;
import com.uscdip.backend.entity.IdempotentRecordEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.DeadLetterStatus;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.IdempotentRecordStatus;
import com.uscdip.backend.repository.DeadLetterRepository;
import com.uscdip.backend.repository.IdempotentRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotentConsumerService {

    public static final String ACTION_CREATE_WORK_ORDER = "CREATE_WORK_ORDER";
    public static final String ACTION_SEND_NOTIFICATION = "SEND_NOTIFICATION";
    public static final String ACTION_WRITEBACK_RESULT = "WRITEBACK_RESULT";

    private static final String SEPARATOR = ":";
    private static final int MAX_ERROR_LENGTH = 4000;

    private final IdempotentRecordRepository idempotentRecordRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final int defaultMaxAttempts;

    public IdempotentConsumerService(
            IdempotentRecordRepository idempotentRecordRepository,
            DeadLetterRepository deadLetterRepository,
            @Value("${backend.idempotency.max-attempts:3}") int defaultMaxAttempts
    ) {
        this.idempotentRecordRepository = idempotentRecordRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.defaultMaxAttempts = Math.max(1, defaultMaxAttempts);
    }

    public String buildKey(String aggregateId, String actionType, Long versionNo) {
        return normalize(aggregateId) + SEPARATOR + normalize(actionType) + SEPARATOR + safeVersion(versionNo);
    }

    @Transactional
    public IdempotentConsumerResult consume(
            String actionType,
            String aggregateType,
            String aggregateId,
            String eventId,
            Long versionNo,
            String payload,
            String traceId,
            Supplier<String> operation
    ) {
        return consume(actionType, aggregateType, aggregateId, eventId, versionNo, payload, traceId, defaultMaxAttempts, operation);
    }

    @Transactional
    public IdempotentConsumerResult consume(
            String actionType,
            String aggregateType,
            String aggregateId,
            String eventId,
            Long versionNo,
            String payload,
            String traceId,
            int maxAttempts,
            Supplier<String> operation
    ) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedAction = normalize(actionType);
        String normalizedAggregateType = normalize(aggregateType);
        String normalizedAggregateId = normalize(aggregateId);
        Long normalizedVersion = safeVersion(versionNo);
        String idempotentKey = buildKey(normalizedAggregateId, normalizedAction, normalizedVersion);

        IdempotentRecordEntity record = idempotentRecordRepository.findById(idempotentKey).orElse(null);
        if (record != null) {
            record.setLastSeenAt(now);
            String status = normalize(record.getStatus());
            if (IdempotentRecordStatus.SUCCESS.name().equals(status)) {
                idempotentRecordRepository.save(record);
                return new IdempotentConsumerResult(idempotentKey, status, record.getResultRefId(), true, false);
            }
            if (IdempotentRecordStatus.PROCESSING.name().equals(status)) {
                idempotentRecordRepository.save(record);
                throw new AuthFlowException(
                        ErrorCode.IDEMPOTENT_CONFLICT,
                        HttpStatus.CONFLICT,
                        "Idempotent key is already processing: " + idempotentKey
                );
            }
            if (IdempotentRecordStatus.DEAD.name().equals(status)) {
                idempotentRecordRepository.save(record);
                return new IdempotentConsumerResult(idempotentKey, status, record.getResultRefId(), true, true);
            }
        } else {
            record = new IdempotentRecordEntity();
            record.setIdempotentKey(idempotentKey);
            record.setActionType(normalizedAction);
            record.setAggregateType(normalizedAggregateType);
            record.setAggregateId(normalizedAggregateId);
            record.setEventId(blankToNull(eventId));
            record.setVersionNo(normalizedVersion);
            record.setFirstSeenAt(now);
            record.setAttemptCount(0);
        }

        record.setStatus(IdempotentRecordStatus.PROCESSING.name());
        record.setLastSeenAt(now);
        record.setCompletedAt(null);
        record.setLastError(null);
        record.setAttemptCount(safeAttemptCount(record.getAttemptCount()) + 1);
        idempotentRecordRepository.save(record);

        try {
            String resultRefId = operation.get();
            LocalDateTime completedAt = LocalDateTime.now();
            record.setStatus(IdempotentRecordStatus.SUCCESS.name());
            record.setResultRefId(resultRefId);
            record.setCompletedAt(completedAt);
            record.setLastSeenAt(completedAt);
            record.setLastError(null);
            idempotentRecordRepository.save(record);
            return new IdempotentConsumerResult(idempotentKey, IdempotentRecordStatus.SUCCESS.name(), resultRefId, false, false);
        } catch (Exception ex) {
            LocalDateTime failedAt = LocalDateTime.now();
            String errorMessage = abbreviateError(ex.getMessage());
            int attempts = safeAttemptCount(record.getAttemptCount());
            boolean dead = attempts >= Math.max(1, maxAttempts);
            record.setStatus(dead ? IdempotentRecordStatus.DEAD.name() : IdempotentRecordStatus.FAILED.name());
            record.setLastSeenAt(failedAt);
            record.setCompletedAt(dead ? failedAt : null);
            record.setLastError(errorMessage);
            idempotentRecordRepository.save(record);
            if (dead) {
                deadLetterRepository.save(new DeadLetterEntity(
                        "DLQ-" + UUID.randomUUID(),
                        blankToNull(eventId),
                        idempotentKey,
                        normalizedAction,
                        normalizedAggregateType,
                        normalizedAggregateId,
                        blankToNull(payload),
                        blankToNull(traceId),
                        errorMessage,
                        attempts,
                        DeadLetterStatus.OPEN.name(),
                        failedAt,
                        null
                ));
            }
            return new IdempotentConsumerResult(idempotentKey, record.getStatus(), record.getResultRefId(), false, dead);
        }
    }

    private Long safeVersion(Long versionNo) {
        return versionNo == null ? 0L : versionNo;
    }

    private int safeAttemptCount(Integer attemptCount) {
        return attemptCount == null ? 0 : attemptCount;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String abbreviateError(String errorMessage) {
        String value = errorMessage == null || errorMessage.isBlank() ? "Unknown idempotent consumer failure" : errorMessage;
        if (value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
