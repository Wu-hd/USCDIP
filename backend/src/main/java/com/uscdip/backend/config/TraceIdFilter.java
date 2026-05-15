package com.uscdip.backend.config;

import com.uscdip.backend.support.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String HTTP_SPAN_ATTRIBUTE = TraceIdFilter.class.getName() + ".httpSpanId";

    private final com.uscdip.backend.service.TraceLinkService traceLinkService;

    public TraceIdFilter(com.uscdip.backend.service.TraceLinkService traceLinkService) {
        this.traceLinkService = traceLinkService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        String spanId = "SPAN-" + UUID.randomUUID();
        request.setAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE, traceId);
        request.setAttribute(HTTP_SPAN_ATTRIBUTE, spanId);
        TraceIdContext.setTraceId(traceId);
        response.setHeader(TraceIdContext.TRACE_ID_HEADER, traceId);
        LocalDateTime recvTime = LocalDateTime.now();
        long startNanos = System.nanoTime();
        traceLinkService.record(new com.uscdip.backend.service.TraceLinkService.TraceLinkCommand(
                null,
                traceId,
                spanId,
                null,
                com.uscdip.backend.service.TraceLinkService.STAGE_HTTP,
                "HTTP_REQUEST_RECEIVED",
                com.uscdip.backend.service.TraceLinkService.SOURCE_HTTP,
                "HTTP_ENDPOINT",
                request.getRequestURI(),
                "RECEIVED",
                null,
                recvTime,
                recvTime,
                currentUserId(),
                clientIp(request),
                request.getMethod() + " " + request.getRequestURI(),
                null,
                null,
                null,
                "query=" + defaultString(request.getQueryString(), "")
        ));
        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int responseStatus = response.getStatus();
            traceLinkService.record(new com.uscdip.backend.service.TraceLinkService.TraceLinkCommand(
                    null,
                    traceId,
                    spanId,
                    null,
                    com.uscdip.backend.service.TraceLinkService.STAGE_HTTP,
                    responseStatus >= 400 ? "HTTP_REQUEST_FAILED" : "HTTP_REQUEST_COMPLETED",
                    com.uscdip.backend.service.TraceLinkService.SOURCE_HTTP,
                    "HTTP_ENDPOINT",
                    request.getRequestURI(),
                    responseStatus >= 400 ? "FAILED" : "SUCCESS",
                    latencyMs,
                    LocalDateTime.now(),
                    recvTime,
                    currentUserId(),
                    clientIp(request),
                    request.getMethod() + " " + request.getRequestURI(),
                    null,
                    null,
                    null,
                    "status=" + responseStatus
            ));
            TraceIdContext.clearTraceId();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object existing = request.getAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE);
        if (existing instanceof String traceId && TraceIdContext.isValidTraceId(traceId)) {
            return traceId;
        }
        return TraceIdContext.resolveIncomingTraceId(
                request.getHeader(TraceIdContext.TRACE_ID_HEADER),
                request.getHeader(TraceIdContext.TRACEPARENT_HEADER)
        );
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.uscdip.backend.service.LocalAccessTokenService.AccessTokenPrincipal accessTokenPrincipal) {
            return accessTokenPrincipal.userId();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
