package com.uscdip.backend.service;

import com.uscdip.backend.entity.SecurityAuditEntity;
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

    private final SecurityAuditRepository securityAuditRepository;

    public SecurityAuditService(SecurityAuditRepository securityAuditRepository) {
        this.securityAuditRepository = securityAuditRepository;
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
        securityAuditRepository.save(new SecurityAuditEntity(
                null,
                eventType,
                userId,
                tokenId,
                sessionId,
                outcome,
                detail,
                clientIp,
                truncate(userAgent, 512),
                TraceIdContext.currentOrGenerate(),
                LocalDateTime.now()
        ));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
