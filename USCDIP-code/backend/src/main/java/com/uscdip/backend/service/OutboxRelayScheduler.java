package com.uscdip.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayScheduler {

    private final OutboxRelayService outboxRelayService;

    public OutboxRelayScheduler(OutboxRelayService outboxRelayService) {
        this.outboxRelayService = outboxRelayService;
    }

    @Scheduled(fixedDelayString = "${backend.outbox.relay-fixed-delay-ms:30000}")
    public void scheduledRelayCycle() {
        outboxRelayService.relayDueEvents();
    }

    public int relayPendingEvents() {
        return outboxRelayService.relayPendingEventsNow();
    }
}
