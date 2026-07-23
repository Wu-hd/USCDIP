package com.uscdip.backend.dto;

public record NotificationConsumeResponse(
        int scannedEvents,
        int consumedNotifications,
        int retriedDeliveries
) {
}
