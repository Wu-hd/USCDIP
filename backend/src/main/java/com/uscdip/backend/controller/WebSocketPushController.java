package com.uscdip.backend.controller;

import com.uscdip.backend.dto.WebSocketAckRequest;
import com.uscdip.backend.dto.WebSocketHeartbeatRequest;
import com.uscdip.backend.model.WebSocketUserPrincipal;
import com.uscdip.backend.service.WebSocketPushGatewayService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WebSocketPushController {

    private final WebSocketPushGatewayService gatewayService;

    public WebSocketPushController(WebSocketPushGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @MessageMapping("/push/heartbeat")
    public void heartbeat(@Payload(required = false) WebSocketHeartbeatRequest request, Principal principal, SimpMessageHeaderAccessor headers) {
        gatewayService.heartbeat((WebSocketUserPrincipal) principal, headers.getSessionId());
    }

    @MessageMapping("/push/ack")
    public void ack(@Valid @Payload WebSocketAckRequest request, Principal principal) {
        gatewayService.ack((WebSocketUserPrincipal) principal, request);
    }
}
