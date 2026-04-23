package com.uscdip.backend.service;

import com.uscdip.backend.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxDispatcher implements OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxDispatcher.class);

    @Override
    public OutboxDispatchResult dispatch(OutboxEventEntity event) {
        log.info(
                "outbox dispatch eventId={} eventType={} aggregateType={} aggregateId={} traceId={}",
                event.getEventId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getTraceId()
        );
        return OutboxDispatchResult.ok();
    }
}
