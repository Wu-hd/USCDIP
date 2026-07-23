package com.uscdip.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebSocketPushScheduler {

    private final WebSocketPushGatewayService gatewayService;

    public WebSocketPushScheduler(WebSocketPushGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Scheduled(fixedDelayString = "${backend.websocket.idle-scan-fixed-delay-ms:30000}")
    public void closeIdleConnections() {
        gatewayService.closeIdleConnections();
    }

    @Scheduled(fixedDelayString = "${backend.websocket.notification-bridge-fixed-delay-ms:30000}")
    public void bridgeNotifications() {
        gatewayService.bridgeNotificationsToPushMessages();
    }
}
