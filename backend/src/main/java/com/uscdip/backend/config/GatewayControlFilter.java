package com.uscdip.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.entity.GatewayRoutePolicyEntity;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.GatewayPolicyService;
import com.uscdip.backend.service.GatewayRateLimitService;
import com.uscdip.backend.support.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class GatewayControlFilter extends OncePerRequestFilter {

    private static final Set<String> FILTERED_PATH_PREFIXES = Set.of(
            "/api/",
            "/v3/api-docs"
    );

    private final GatewayPolicyService gatewayPolicyService;
    private final GatewayRateLimitService gatewayRateLimitService;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    public GatewayControlFilter(
            GatewayPolicyService gatewayPolicyService,
            GatewayRateLimitService gatewayRateLimitService,
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper
    ) {
        this.gatewayPolicyService = gatewayPolicyService;
        this.gatewayRateLimitService = gatewayRateLimitService;
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return FILTERED_PATH_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        ensureTraceId(request, response);

        GatewayRoutePolicyEntity policy = gatewayPolicyService.resolvePolicy(requestPath, request.getMethod()).orElse(null);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        GatewayPolicyService.UserContext userContext = gatewayPolicyService.resolveUserContext(authentication);
        String clientIp = resolveClientIp(request);

        GatewayPolicyService.RuleDecision ruleDecision = gatewayPolicyService.evaluateClientRules(userContext.userId(), clientIp);
        if ("BLOCKLIST_MATCHED".equals(ruleDecision.decision())) {
            gatewayPolicyService.recordAudit(policy, requestPath, userContext.userId(), userContext.authMode(), "DENY", "BLOCKLIST_MATCHED", clientIp);
            writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.GATEWAY_BLOCKED, ruleDecision.reason());
            return;
        }

        if (policy != null && policy.isAuthRequired()
                && (authentication == null || !authentication.isAuthenticated() || !StringUtils.hasText(userContext.userId()))) {
            gatewayPolicyService.recordAudit(policy, requestPath, userContext.userId(), userContext.authMode(), "DENY", "AUTH_REQUIRED", clientIp);
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
            return;
        }

        if (request.getContentLengthLong() > gatewayProperties.getBodySizeLimitBytes()) {
            gatewayPolicyService.recordAudit(policy, requestPath, userContext.userId(), userContext.authMode(), "DENY", "REQUEST_BODY_TOO_LARGE", clientIp);
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, ErrorCode.REQUEST_BODY_TOO_LARGE, ErrorCode.REQUEST_BODY_TOO_LARGE.defaultMessage());
            return;
        }

        if (gatewayPolicyService.requiresJsonBody(request.getMethod(), request.getContentLengthLong(), policy)
                && !isJsonContentType(request.getContentType())) {
            gatewayPolicyService.recordAudit(policy, requestPath, userContext.userId(), userContext.authMode(), "DENY", "UNSUPPORTED_CONTENT_TYPE", clientIp);
            writeError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_CONTENT_TYPE, ErrorCode.UNSUPPORTED_CONTENT_TYPE.defaultMessage());
            return;
        }

        if (gatewayPolicyService.validatesPageQuery(policy)) {
            Integer page = parsePositiveInteger(request.getParameter("page"));
            Integer pageSize = parsePositiveInteger(request.getParameter("pageSize"));
            if (page != null && page < 1) {
                gatewayPolicyService.recordAudit(policy, requestPath, userContext.userId(), userContext.authMode(), "DENY", "INVALID_PAGE", clientIp);
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, ErrorCode.INVALID_PARAMETER, "page must be greater than or equal to 1");
                return;
            }
            if (pageSize != null && (pageSize < 1 || pageSize > gatewayProperties.getPageSizeMax())) {
                gatewayPolicyService.recordAudit(policy, requestPath, userContext.userId(), userContext.authMode(), "DENY", "INVALID_PAGE_SIZE", clientIp);
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, ErrorCode.INVALID_PARAMETER, "pageSize must be between 1 and " + gatewayProperties.getPageSizeMax());
                return;
            }
        }

        String rateLimitKey = gatewayPolicyService.resolveRateLimitKey(policy, userContext.userId(), clientIp);
        int capacity = gatewayPolicyService.resolveCapacity(policy, gatewayProperties.getDefaultCapacity());
        int windowSeconds = gatewayPolicyService.resolveWindowSeconds(policy, gatewayProperties.getDefaultWindowSeconds());
        if (!gatewayRateLimitService.tryAcquire(rateLimitKey, capacity, windowSeconds)) {
            gatewayPolicyService.recordAudit(policy, requestPath, userContext.userId(), userContext.authMode(), "DENY", "RATE_LIMITED", clientIp);
            writeError(response, 429, ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.defaultMessage());
            return;
        }

        if (gatewayPolicyService.shouldAuditAllow(policy)) {
            gatewayPolicyService.recordAudit(policy, requestPath, userContext.userId(), userContext.authMode(), "ALLOW", "ALLOW", clientIp);
        }

        filterChain.doFilter(request, response);
    }

    private void ensureTraceId(HttpServletRequest request, HttpServletResponse response) {
        String traceId = TraceIdContext.getTraceId();
        if (!TraceIdContext.isValidTraceId(traceId)) {
            traceId = TraceIdContext.resolveIncomingTraceId(
                    request.getHeader(TraceIdContext.TRACE_ID_HEADER),
                    request.getHeader(TraceIdContext.TRACEPARENT_HEADER)
            );
            TraceIdContext.setTraceId(traceId);
        }
        request.setAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE, traceId);
        response.setHeader(TraceIdContext.TRACE_ID_HEADER, traceId);
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.toLowerCase();
        return normalized.startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    private Integer parsePositiveInteger(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(errorCode, message));
    }
}
