package com.uscdip.backend.service;

import com.uscdip.backend.entity.OutboxEventEntity;
import com.uscdip.backend.model.OutboxEventStatus;
import com.uscdip.backend.repository.OutboxEventRepository;
import com.uscdip.backend.support.TraceIdContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxRelayService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxDispatcher outboxDispatcher;
    private final TraceLinkService traceLinkService;
    private final int batchSize;
    private final int maxAttempts;
    private final int retryBaseDelaySeconds;
    private final int claimTimeoutSeconds;
    private final String instanceId;

    public OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            OutboxDispatcher outboxDispatcher,
            TraceLinkService traceLinkService,
            @Value("${backend.outbox.batch-size:20}") int batchSize,
            @Value("${backend.outbox.max-attempts:3}") int maxAttempts,
            @Value("${backend.outbox.retry-base-delay-seconds:30}") int retryBaseDelaySeconds,
            @Value("${backend.outbox.claim-timeout-seconds:120}") int claimTimeoutSeconds,
            @Value("${backend.outbox.instance-id:}") String configuredInstanceId
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxDispatcher = outboxDispatcher;
        this.traceLinkService = traceLinkService;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBaseDelaySeconds = Math.max(1, retryBaseDelaySeconds);
        this.claimTimeoutSeconds = Math.max(1, claimTimeoutSeconds);
        this.instanceId = configuredInstanceId == null || configuredInstanceId.isBlank()
                ? "outbox-relay-" + UUID.randomUUID()
                : configuredInstanceId;
    }

    @Transactional
    public int relayDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        resetTimedOutClaims(now);
        List<OutboxEventEntity> claimedEvents = claimDueEvents(now);
        for (OutboxEventEntity event : claimedEvents) {
            dispatchClaimedEvent(event, LocalDateTime.now());
        }
        return claimedEvents.size();
    }

    @Transactional
    public int relayPendingEventsNow() {
        return relayDueEvents();
    }

    private void resetTimedOutClaims(LocalDateTime now) {
        LocalDateTime timeoutBefore = now.minusSeconds(claimTimeoutSeconds);
        List<OutboxEventEntity> timedOutEvents = outboxEventRepository.findByStatusAndClaimedAtBeforeOrderByCreatedAtAsc(
                OutboxEventStatus.SENDING.name(),
                timeoutBefore
        );
        for (OutboxEventEntity event : timedOutEvents) {
            event.setStatus(OutboxEventStatus.FAILED.name());
            event.setNextRetryTime(now);
            event.setClaimedBy(null);
            event.setClaimedAt(null);
            event.setLastError(abbreviateError("Relay claim timed out"));
            event.setUpdatedAt(now);
        }
        if (!timedOutEvents.isEmpty()) {
            outboxEventRepository.saveAll(timedOutEvents);
        }
    }

    private List<OutboxEventEntity> claimDueEvents(LocalDateTime now) {
        List<OutboxEventEntity> dueEvents = outboxEventRepository.findByStatusInAndNextRetryTimeLessThanEqualOrderByCreatedAtAsc(
                List.of(OutboxEventStatus.NEW.name(), OutboxEventStatus.FAILED.name()),
                now,
                PageRequest.of(0, batchSize)
        );
        for (OutboxEventEntity event : dueEvents) {
            event.setStatus(OutboxEventStatus.SENDING.name());
            event.setClaimedBy(instanceId);
            event.setClaimedAt(now);
            event.setUpdatedAt(now);
        }
        return outboxEventRepository.saveAll(dueEvents);
    }

    private void dispatchClaimedEvent(OutboxEventEntity event, LocalDateTime now) {
        long startNanos = System.nanoTime();
        try {
            OutboxDispatchResult result = TraceIdContext.withTraceId(event.getTraceId(), () -> outboxDispatcher.dispatch(event));
            if (result != null && result.success()) {
                markSent(event, now);
                recordDispatchTrace(event, "OUTBOX_DISPATCH_SUCCESS", event.getStatus(), null, elapsedMillis(startNanos));
                return;
            }
            markFailed(event, result == null ? "Outbox dispatcher returned null result" : result.errorMessage(), now);
            recordDispatchTrace(event, failureEventType(event), event.getStatus(), event.getLastError(), elapsedMillis(startNanos));
        } catch (Exception ex) {
            markFailed(event, ex.getMessage(), now);
            recordDispatchTrace(event, failureEventType(event), event.getStatus(), event.getLastError(), elapsedMillis(startNanos));
        }
    }

    private void markSent(OutboxEventEntity event, LocalDateTime now) {
        event.setStatus(OutboxEventStatus.SENT.name());
        event.setSentAt(now);
        event.setNextRetryTime(null);
        event.setClaimedBy(null);
        event.setClaimedAt(null);
        event.setLastError(null);
        event.setUpdatedAt(now);
        outboxEventRepository.save(event);
    }

    private void markFailed(OutboxEventEntity event, String errorMessage, LocalDateTime now) {
        int nextRetryCount = safeRetryCount(event.getRetryCount()) + 1;
        event.setRetryCount(nextRetryCount);
        event.setLastError(abbreviateError(errorMessage));
        event.setClaimedBy(null);
        event.setClaimedAt(null);
        event.setUpdatedAt(now);
        if (nextRetryCount >= maxAttempts) {
            event.setStatus(OutboxEventStatus.DEAD.name());
            event.setNextRetryTime(null);
        } else {
            event.setStatus(OutboxEventStatus.FAILED.name());
            event.setNextRetryTime(computeNextRetryTime(nextRetryCount, now));
        }
        outboxEventRepository.save(event);
    }

    private LocalDateTime computeNextRetryTime(int retryCount, LocalDateTime now) {
        long delaySeconds = (long) retryBaseDelaySeconds * (1L << Math.max(0, retryCount - 1));
        return now.plusSeconds(delaySeconds);
    }

    private int safeRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }

    private String abbreviateError(String errorMessage) {
        String value = errorMessage == null || errorMessage.isBlank() ? "Unknown outbox dispatch failure" : errorMessage;
        if (value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    private void recordDispatchTrace(OutboxEventEntity event, String eventType, String status, String detail, long latencyMs) {
        traceLinkService.record(new TraceLinkService.TraceLinkCommand(
                null,
                event.getTraceId(),
                "OUTBOX-" + event.getEventId(),
                null,
                TraceLinkService.STAGE_OUTBOX,
                eventType,
                TraceLinkService.SOURCE_OUTBOX,
                event.getAggregateType(),
                event.getAggregateId(),
                status,
                latencyMs,
                LocalDateTime.now(),
                event.getCreatedAt(),
                null,
                null,
                null,
                event.getEventId(),
                null,
                null,
                detail
        ));
    }

    private String failureEventType(OutboxEventEntity event) {
        return OutboxEventStatus.DEAD.name().equals(event.getStatus())
                ? "OUTBOX_DISPATCH_DEAD"
                : "OUTBOX_DISPATCH_FAILED";
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
