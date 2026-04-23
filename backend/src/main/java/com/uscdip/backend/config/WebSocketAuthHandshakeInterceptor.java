package com.uscdip.backend.config;

import com.uscdip.backend.model.WebSocketUserPrincipal;
import com.uscdip.backend.service.WebSocketPushGatewayService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_PRINCIPAL = "wsPrincipal";

    private final WebSocketPushGatewayService gatewayService;

    public WebSocketAuthHandshakeInterceptor(WebSocketPushGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String origin = request.getHeaders().getOrigin();
        if (!gatewayService.isOriginAllowed(origin)) {
            return false;
        }
        try {
            WebSocketUserPrincipal principal = gatewayService.authenticateHandshake(
                    resolveToken(request),
                    resolveClientIp(request),
                    request.getHeaders().getFirst(HttpHeaders.USER_AGENT)
            );
            attributes.put(ATTR_PRINCIPAL, principal);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.toLowerCase().startsWith("bearer ")) {
            return authorization.substring("bearer ".length()).trim();
        }
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("access_token");
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getRemoteAddr();
        }
        return "unknown";
    }
}
