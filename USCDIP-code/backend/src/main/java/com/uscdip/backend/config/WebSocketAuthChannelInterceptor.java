package com.uscdip.backend.config;

import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.WebSocketUserPrincipal;
import com.uscdip.backend.service.WebSocketPushGatewayService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final WebSocketPushGatewayService gatewayService;

    public WebSocketAuthChannelInterceptor(WebSocketPushGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        WebSocketUserPrincipal principal = resolvePrincipal(accessor);
        try {
            if (StompCommand.CONNECT.equals(command)) {
                gatewayService.attachStompSession(principal, accessor.getSessionId());
            } else if (StompCommand.SUBSCRIBE.equals(command)) {
                gatewayService.subscribe(principal, accessor.getSessionId(), accessor.getSubscriptionId(), accessor.getDestination());
            } else if (StompCommand.SEND.equals(command) || SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
                if (!gatewayService.isAckDestinationAllowed(accessor.getDestination())) {
                    throw new MessagingException("WebSocket SEND destination is not allowed");
                }
                gatewayService.heartbeat(principal, accessor.getSessionId());
            }
            return message;
        } catch (AuthFlowException ex) {
            throw new MessagingException(ex.getMessage(), ex);
        }
    }

    private WebSocketUserPrincipal resolvePrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof WebSocketUserPrincipal principal) {
            return principal;
        }
        throw new MessagingException("WebSocket principal is missing");
    }
}
