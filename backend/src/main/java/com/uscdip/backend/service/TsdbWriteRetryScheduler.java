package com.uscdip.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TsdbWriteRetryScheduler {

    private final TsdbWriteService tsdbWriteService;

    public TsdbWriteRetryScheduler(TsdbWriteService tsdbWriteService) {
        this.tsdbWriteService = tsdbWriteService;
    }

    @Scheduled(fixedDelayString = "${backend.tsdb.retry-fixed-delay-ms:30000}")
    public void scheduledRetryCycle() {
        tsdbWriteService.retryDueWrites();
    }

    public int retryPendingWrites() {
        return tsdbWriteService.retryDueWrites();
    }
}
