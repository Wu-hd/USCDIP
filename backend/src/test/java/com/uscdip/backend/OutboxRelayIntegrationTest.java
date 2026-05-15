package com.uscdip.backend;

import com.uscdip.backend.entity.OutboxEventEntity;
import com.uscdip.backend.model.OutboxEventStatus;
import com.uscdip.backend.repository.OutboxEventRepository;
import com.uscdip.backend.service.OutboxDispatchResult;
import com.uscdip.backend.service.OutboxDispatcher;
import com.uscdip.backend.service.OutboxRelayScheduler;
import com.uscdip.backend.service.OutboxRelayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000",
        "backend.outbox.batch-size=10",
        "backend.outbox.max-attempts=2",
        "backend.outbox.retry-base-delay-seconds=1",
        "backend.outbox.claim-timeout-seconds=60",
        "backend.outbox.instance-id=test-relay"
})
class OutboxRelayIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @MockBean
    private OutboxDispatcher outboxDispatcher;

    @MockBean
    private OutboxRelayScheduler outboxRelayScheduler;

    @AfterEach
    void resetDispatcher() {
        Mockito.reset(outboxDispatcher);
    }

    @Test
    void relayMarksDueEventAsSentWhenDispatcherSucceeds() {
        OutboxEventEntity event = saveOutboxEvent(OutboxEventStatus.NEW, 0, LocalDateTime.now().minusSeconds(1));
        when(outboxDispatcher.dispatch(argThat(candidate -> event.getEventId().equals(candidate.getEventId()))))
                .thenReturn(OutboxDispatchResult.ok());

        int processed = outboxRelayService.relayDueEvents();

        OutboxEventEntity reloaded = outboxEventRepository.findById(event.getEventId()).orElseThrow();
        Assertions.assertTrue(processed >= 1);
        Assertions.assertEquals(OutboxEventStatus.SENT.name(), reloaded.getStatus());
        Assertions.assertNotNull(reloaded.getSentAt());
        Assertions.assertNull(reloaded.getNextRetryTime());
        Assertions.assertNull(reloaded.getClaimedBy());
        Assertions.assertNull(reloaded.getLastError());
    }

    @Test
    void relayRecordsFailureAndSchedulesRetryBeforeMaxAttempts() {
        LocalDateTime beforeRelay = LocalDateTime.now();
        OutboxEventEntity event = saveOutboxEvent(OutboxEventStatus.FAILED, 0, beforeRelay.minusSeconds(1));
        when(outboxDispatcher.dispatch(argThat(candidate -> event.getEventId().equals(candidate.getEventId()))))
                .thenReturn(OutboxDispatchResult.failure("temporary broker outage"));

        outboxRelayService.relayDueEvents();

        OutboxEventEntity reloaded = outboxEventRepository.findById(event.getEventId()).orElseThrow();
        Assertions.assertEquals(OutboxEventStatus.FAILED.name(), reloaded.getStatus());
        Assertions.assertEquals(1, reloaded.getRetryCount());
        Assertions.assertTrue(reloaded.getLastError().contains("temporary broker outage"));
        Assertions.assertNotNull(reloaded.getNextRetryTime());
        Assertions.assertTrue(reloaded.getNextRetryTime().isAfter(beforeRelay));
        Assertions.assertNull(reloaded.getClaimedBy());
        Assertions.assertNull(reloaded.getSentAt());
    }

    @Test
    void relayMovesEventToDeadAfterMaxAttempts() {
        OutboxEventEntity event = saveOutboxEvent(OutboxEventStatus.FAILED, 1, LocalDateTime.now().minusSeconds(1));
        when(outboxDispatcher.dispatch(argThat(candidate -> event.getEventId().equals(candidate.getEventId()))))
                .thenThrow(new IllegalStateException("poison message"));

        outboxRelayService.relayDueEvents();

        OutboxEventEntity reloaded = outboxEventRepository.findById(event.getEventId()).orElseThrow();
        Assertions.assertEquals(OutboxEventStatus.DEAD.name(), reloaded.getStatus());
        Assertions.assertEquals(2, reloaded.getRetryCount());
        Assertions.assertTrue(reloaded.getLastError().contains("poison message"));
        Assertions.assertNull(reloaded.getNextRetryTime());
        Assertions.assertNull(reloaded.getClaimedBy());
        Assertions.assertNull(reloaded.getSentAt());
    }

    private OutboxEventEntity saveOutboxEvent(OutboxEventStatus status, int retryCount, LocalDateTime nextRetryTime) {
        LocalDateTime now = LocalDateTime.now();
        String suffix = UUID.randomUUID().toString();
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId("OBE-TEST-" + suffix);
        event.setAggregateType("INCIDENT");
        event.setAggregateId("INC-TEST-" + suffix);
        event.setEventType("INCIDENT_UPDATED");
        event.setPayload("{\"incidentId\":\"INC-TEST-" + suffix + "\",\"status\":\"OPEN\",\"severity\":\"HIGH\",\"versionNo\":1,\"sourceCaseId\":\"ALCASE-TEST\",\"sourceAlertId\":\"ALERT-TEST\",\"traceId\":\"TRACE-OUTBOX-TEST\",\"occurredAt\":\"2026-04-23T08:00:00\"}");
        event.setTraceId("TRACE-OUTBOX-TEST");
        event.setStatus(status.name());
        event.setRetryCount(retryCount);
        event.setNextRetryTime(nextRetryTime);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return outboxEventRepository.save(event);
    }
}
