package com.uscdip.backend.config;

import com.uscdip.backend.service.WebSocketPushGatewayService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionEventListener {

    private final WebSocketPushGatewayService gatewayService;

    public WebSocketSessionEventListener(WebSocketPushGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        gatewayService.disconnect(event.getSessionId(), "CLIENT_DISCONNECT");
    }
}
