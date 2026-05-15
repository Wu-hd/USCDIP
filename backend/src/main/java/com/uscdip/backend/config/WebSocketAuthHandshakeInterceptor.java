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
        String traceId = com.uscdip.backend.support.TraceIdContext.resolveIncomingTraceId(
                request.getHeaders().getFirst(com.uscdip.backend.support.TraceIdContext.TRACE_ID_HEADER),
                request.getHeaders().getFirst(com.uscdip.backend.support.TraceIdContext.TRACEPARENT_HEADER)
        );
        try {
            WebSocketUserPrincipal principal = com.uscdip.backend.support.TraceIdContext.withTraceId(traceId, () ->
                    gatewayService.authenticateHandshake(
                            resolveToken(request),
                            resolveClientIp(request),
                            request.getHeaders().getFirst(HttpHeaders.USER_AGENT),
                            traceId
                    ));
            attributes.put(ATTR_PRINCIPAL, principal);
            attributes.put(com.uscdip.backend.support.TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE, traceId);
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
