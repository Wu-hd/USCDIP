package com.uscdip.backend;

import com.uscdip.backend.entity.DeadLetterEntity;
import com.uscdip.backend.entity.IdempotentRecordEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.DeadLetterStatus;
import com.uscdip.backend.model.IdempotentRecordStatus;
import com.uscdip.backend.repository.DeadLetterRepository;
import com.uscdip.backend.repository.IdempotentRecordRepository;
import com.uscdip.backend.service.IdempotentConsumerResult;
import com.uscdip.backend.service.IdempotentConsumerService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000",
        "backend.idempotency.max-attempts=2"
})
class IdempotentConsumerServiceIntegrationTest {

    @Autowired
    private IdempotentConsumerService idempotentConsumerService;

    @Autowired
    private IdempotentRecordRepository idempotentRecordRepository;

    @Autowired
    private DeadLetterRepository deadLetterRepository;

    @Test
    void successfulDuplicateReturnsStoredResultWithoutRunningOperationAgain() {
        String aggregateId = "INC-IDEMP-" + UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger();

        IdempotentConsumerResult first = idempotentConsumerService.consume(
                IdempotentConsumerService.ACTION_CREATE_WORK_ORDER,
                "INCIDENT",
                aggregateId,
                "OBE-IDEMP-SUCCESS",
                1L,
                "{\"incidentId\":\"" + aggregateId + "\"}",
                "TRACE-IDEMP-SUCCESS",
                () -> {
                    executions.incrementAndGet();
                    return "WO-IDEMP-" + UUID.randomUUID();
                }
        );

        IdempotentConsumerResult second = idempotentConsumerService.consume(
                IdempotentConsumerService.ACTION_CREATE_WORK_ORDER,
                "INCIDENT",
                aggregateId,
                "OBE-IDEMP-SUCCESS",
                1L,
                "{\"incidentId\":\"" + aggregateId + "\"}",
                "TRACE-IDEMP-SUCCESS",
                () -> {
                    executions.incrementAndGet();
                    return "WO-IDEMP-SHOULD-NOT-RUN";
                }
        );

        Assertions.assertEquals(1, executions.get());
        Assertions.assertEquals(first.resultRefId(), second.resultRefId());
        Assertions.assertTrue(second.duplicate());
        IdempotentRecordEntity record = idempotentRecordRepository.findById(first.idempotentKey()).orElseThrow();
        Assertions.assertEquals(IdempotentRecordStatus.SUCCESS.name(), record.getStatus());
        Assertions.assertEquals(first.resultRefId(), record.getResultRefId());
    }

    @Test
    void processingDuplicateIsRejectedAsIdempotentConflict() {
        String aggregateId = "INC-IDEMP-PROCESSING-" + UUID.randomUUID();
        String key = idempotentConsumerService.buildKey(
                aggregateId,
                IdempotentConsumerService.ACTION_SEND_NOTIFICATION,
                1L
        );
        idempotentRecordRepository.save(new IdempotentRecordEntity(
                key,
                IdempotentConsumerService.ACTION_SEND_NOTIFICATION,
                "INCIDENT",
                aggregateId,
                "OBE-IDEMP-PROCESSING",
                1L,
                IdempotentRecordStatus.PROCESSING.name(),
                null,
                1,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                null,
                null
        ));

        Assertions.assertThrows(AuthFlowException.class, () -> idempotentConsumerService.consume(
                IdempotentConsumerService.ACTION_SEND_NOTIFICATION,
                "INCIDENT",
                aggregateId,
                "OBE-IDEMP-PROCESSING",
                1L,
                "{}",
                "TRACE-IDEMP-PROCESSING",
                () -> "NOTIFY-SHOULD-NOT-RUN"
        ));
    }

    @Test
    void repeatedFailuresCreateDeadLetterAtMaxAttempts() {
        String aggregateId = "INC-IDEMP-DEAD-" + UUID.randomUUID();

        IdempotentConsumerResult first = idempotentConsumerService.consume(
                IdempotentConsumerService.ACTION_WRITEBACK_RESULT,
                "INCIDENT",
                aggregateId,
                "OBE-IDEMP-DEAD",
                2L,
                "{\"incidentId\":\"" + aggregateId + "\"}",
                "TRACE-IDEMP-DEAD",
                2,
                () -> {
                    throw new IllegalStateException("writeback target unavailable");
                }
        );
        IdempotentConsumerResult second = idempotentConsumerService.consume(
                IdempotentConsumerService.ACTION_WRITEBACK_RESULT,
                "INCIDENT",
                aggregateId,
                "OBE-IDEMP-DEAD",
                2L,
                "{\"incidentId\":\"" + aggregateId + "\"}",
                "TRACE-IDEMP-DEAD",
                2,
                () -> {
                    throw new IllegalStateException("writeback target unavailable");
                }
        );

        Assertions.assertEquals(IdempotentRecordStatus.FAILED.name(), first.status());
        Assertions.assertEquals(IdempotentRecordStatus.DEAD.name(), second.status());
        Assertions.assertTrue(second.dead());

        IdempotentRecordEntity record = idempotentRecordRepository.findById(second.idempotentKey()).orElseThrow();
        Assertions.assertEquals(IdempotentRecordStatus.DEAD.name(), record.getStatus());
        Assertions.assertEquals(2, record.getAttemptCount());
        Assertions.assertTrue(record.getLastError().contains("writeback target unavailable"));

        List<DeadLetterEntity> letters = deadLetterRepository.findByIdempotentKeyOrderByCreatedAtAsc(second.idempotentKey());
        Assertions.assertEquals(1, letters.size());
        Assertions.assertEquals(DeadLetterStatus.OPEN.name(), letters.get(0).getStatus());
        Assertions.assertEquals(2, letters.get(0).getRetryCount());
        Assertions.assertTrue(letters.get(0).getFailureReason().contains("writeback target unavailable"));
    }
}
