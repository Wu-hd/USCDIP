package com.uscdip.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetryScheduler {

    private final NotificationService notificationService;

    public NotificationRetryScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${backend.notification.retry-fixed-delay-ms:30000}")
    public void retryDueDeliveries() {
        notificationService.retryDueDeliveries();
    }
}
