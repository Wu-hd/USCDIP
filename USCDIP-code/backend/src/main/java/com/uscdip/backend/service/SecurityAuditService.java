package com.uscdip.backend.service;

import com.uscdip.backend.entity.SecurityAuditEntity;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.repository.SecurityAuditRepository;
import com.uscdip.backend.support.TraceIdContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SecurityAuditService {

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_DENY = "DENY";
    public static final String EVENT_OIDC_CALLBACK_SUCCESS = "OIDC_CALLBACK_SUCCESS";
    public static final String EVENT_OIDC_STATE_INVALID = "OIDC_STATE_INVALID";
    public static final String EVENT_REFRESH_SUCCESS = "TOKEN_REFRESH_SUCCESS";
    public static final String EVENT_REFRESH_REPLAY = "TOKEN_REFRESH_REPLAY";
    public static final String EVENT_REFRESH_REVOKED = "TOKEN_REFRESH_REVOKED";
    public static final String EVENT_REFRESH_EXPIRED = "TOKEN_REFRESH_EXPIRED";
    public static final String EVENT_LOGOUT_SUCCESS = "TOKEN_LOGOUT_SUCCESS";
    public static final String EVENT_SESSION_REVOKED = "TOKEN_SESSION_REVOKED";
    public static final String EVENT_USER_DISABLED = "TOKEN_USER_DISABLED";
    public static final String EVENT_PERMISSION_REVOKED = "TOKEN_PERMISSION_REVOKED";
    public static final String EVENT_ACCOUNT_CREATED = "ACCOUNT_CREATED";
    public static final String EVENT_ACCOUNT_UPDATED = "ACCOUNT_UPDATED";
    public static final String EVENT_ACCOUNT_ENABLED = "ACCOUNT_ENABLED";
    public static final String EVENT_ACCOUNT_PASSWORD_RESET = "ACCOUNT_PASSWORD_RESET";
    public static final String EVENT_ACCESS_REJECTED = "TOKEN_ACCESS_REJECTED";
    public static final String EVENT_BREAK_GLASS_LOGIN_SUCCESS = "BREAK_GLASS_LOGIN_SUCCESS";
    public static final String EVENT_BREAK_GLASS_LOGIN_FAILED = "BREAK_GLASS_LOGIN_FAILED";
    public static final String EVENT_BREAK_GLASS_ACCOUNT_ACTIVATED = "BREAK_GLASS_ACCOUNT_ACTIVATED";
    public static final String EVENT_BREAK_GLASS_ACCOUNT_REVOKED = "BREAK_GLASS_ACCOUNT_REVOKED";
    public static final String EVENT_BREAK_GLASS_TOKEN_REFRESH = "BREAK_GLASS_TOKEN_REFRESH";
    public static final String EVENT_BREAK_GLASS_ACCESS_REJECTED = "BREAK_GLASS_ACCESS_REJECTED";

    private final SecurityAuditRepository securityAuditRepository;
    private final UnifiedAuditService unifiedAuditService;

    public SecurityAuditService(SecurityAuditRepository securityAuditRepository, UnifiedAuditService unifiedAuditService) {
        this.securityAuditRepository = securityAuditRepository;
        this.unifiedAuditService = unifiedAuditService;
    }

    public void log(
            String eventType,
            String userId,
            String tokenId,
            String sessionId,
            String outcome,
            String detail,
            String clientIp,
            String userAgent
    ) {
        log(eventType, userId, tokenId, sessionId, null, null, outcome, detail, clientIp, userAgent);
    }

    public void log(
            String eventType,
            String userId,
            String tokenId,
            String sessionId,
            String authMode,
            String emergencyAccountId,
            String outcome,
            String detail,
            String clientIp,
            String userAgent
    ) {
        SecurityAuditEntity saved = securityAuditRepository.save(new SecurityAuditEntity(
                null,
                eventType,
                userId,
                tokenId,
                sessionId,
                authMode == null ? AuthMode.STANDARD : authMode,
                emergencyAccountId,
                outcome,
                detail,
                clientIp,
                truncate(userAgent, 512),
                TraceIdContext.currentOrGenerate(),
                LocalDateTime.now()
        ));
        unifiedAuditService.recordSecurityAudit(
                eventType,
                userId,
                authMode == null ? AuthMode.STANDARD : authMode,
                emergencyAccountId,
                outcome,
                detail,
                clientIp,
                userAgent,
                saved.getId() == null ? null : String.valueOf(saved.getId())
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
