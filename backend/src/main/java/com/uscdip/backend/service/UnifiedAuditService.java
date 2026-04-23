package com.uscdip.backend.service;

import com.uscdip.backend.dto.AuditLogResponse;
import com.uscdip.backend.entity.AuditLogEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.AuditLogRepository;
import com.uscdip.backend.support.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class UnifiedAuditService {

    public static final String CATEGORY_AUTH = "AUTH";
    public static final String CATEGORY_GATEWAY = "GATEWAY";
    public static final String CATEGORY_WORK_ORDER = "WORK_ORDER";
    public static final String CATEGORY_MODEL = "MODEL";
    public static final String CATEGORY_FEATURE_VIEW = "FEATURE_VIEW";
    public static final String CATEGORY_BREAK_GLASS = "BREAK_GLASS";

    public static final String SOURCE_SECURITY = "security";
    public static final String SOURCE_GATEWAY = "gateway";
    public static final String SOURCE_WORK_ORDER = "work_order";
    public static final String SOURCE_MODEL_GATEWAY = "model_gateway";
    public static final String SOURCE_FEATURE_VIEW = "feature_view";
    public static final String SOURCE_AUTHZ_GUARD = "authz_guard";

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_ALLOW = "ALLOW";
    public static final String OUTCOME_DENY = "DENY";

    private final AuditLogRepository auditLogRepository;

    public UnifiedAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogEntity record(AuditLogCommand command) {
        LocalDateTime now = command.eventTime() == null ? LocalDateTime.now() : command.eventTime();
        PrincipalSnapshot principal = currentPrincipal();
        HttpSnapshot http = currentHttp();
        AuditLogEntity entity = new AuditLogEntity(
                "AUD-" + UUID.randomUUID(),
                now,
                required(command.eventCategory(), "eventCategory"),
                required(command.eventType(), "eventType"),
                required(command.sourceModule(), "sourceModule"),
                firstText(command.actorUserId(), principal.userId()),
                firstText(command.actorUsername(), principal.username()),
                firstText(command.authMode(), principal.authMode(), AuthMode.STANDARD),
                firstText(command.emergencyAccountId(), principal.emergencyAccountId()),
                firstText(command.outcome(), OUTCOME_SUCCESS),
                command.riskLevel(),
                command.objectType(),
                command.objectId(),
                firstText(command.action(), command.eventType()),
                firstText(command.httpMethod(), http.method()),
                firstText(command.requestPath(), http.path()),
                firstText(command.clientIp(), http.clientIp()),
                truncate(firstText(command.userAgent(), http.userAgent()), 512),
                firstText(command.traceId(), TraceIdContext.currentOrGenerate()),
                truncate(command.detail(), 2000),
                command.beforeSnapshot(),
                command.afterSnapshot(),
                command.sourceTable(),
                command.sourceRecordId()
        );
        return auditLogRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listAuditLogs(
            int page,
            int pageSize,
            String eventCategory,
            String eventType,
            String sourceModule,
            String actorUserId,
            String authMode,
            String emergencyAccountId,
            String outcome,
            String riskLevel,
            String objectType,
            String objectId,
            String traceId,
            LocalDateTime from,
            LocalDateTime to,
            boolean emergencyOnly
    ) {
        List<AuditLogResponse> items = auditLogRepository.findAllByOrderByEventTimeDescAuditIdDesc().stream()
                .filter(audit -> matches(audit.getEventCategory(), eventCategory))
                .filter(audit -> matches(audit.getEventType(), eventType))
                .filter(audit -> matches(audit.getSourceModule(), sourceModule))
                .filter(audit -> matches(audit.getActorUserId(), actorUserId))
                .filter(audit -> matches(audit.getAuthMode(), authMode))
                .filter(audit -> matches(audit.getEmergencyAccountId(), emergencyAccountId))
                .filter(audit -> matches(audit.getOutcome(), outcome))
                .filter(audit -> matches(audit.getRiskLevel(), riskLevel))
                .filter(audit -> matches(audit.getObjectType(), objectType))
                .filter(audit -> matches(audit.getObjectId(), objectId))
                .filter(audit -> matches(audit.getTraceId(), traceId))
                .filter(audit -> inRange(audit.getEventTime(), from, to))
                .filter(audit -> !emergencyOnly || isEmergencyAudit(audit))
                .map(this::toResponse)
                .toList();
        return paginate(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLog(String auditId) {
        return auditLogRepository.findById(auditId)
                .map(this::toResponse)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.AUDIT_LOG_NOT_FOUND, HttpStatus.NOT_FOUND, "Audit log not found: " + auditId));
    }

    public void recordSecurityAudit(
            String eventType,
            String userId,
            String authMode,
            String emergencyAccountId,
            String outcome,
            String detail,
            String clientIp,
            String userAgent,
            String sourceRecordId
    ) {
        record(new AuditLogCommand(
                null,
                isBreakGlass(eventType, authMode, emergencyAccountId) ? CATEGORY_BREAK_GLASS : CATEGORY_AUTH,
                eventType,
                SOURCE_SECURITY,
                userId,
                null,
                authMode,
                emergencyAccountId,
                outcome,
                resolveRisk(eventType),
                "USER",
                userId,
                eventType,
                null,
                null,
                clientIp,
                userAgent,
                null,
                detail,
                null,
                null,
                "security_audit",
                sourceRecordId
        ));
    }

    public void recordWorkOrderOperation(AuthorizationContext context, String eventType, String workOrderId, String detail) {
        record(new AuditLogCommand(
                null,
                CATEGORY_WORK_ORDER,
                eventType,
                SOURCE_WORK_ORDER,
                context.userId(),
                context.username(),
                null,
                null,
                OUTCOME_SUCCESS,
                "HIGH",
                "WORK_ORDER",
                workOrderId,
                eventType,
                null,
                null,
                null,
                null,
                null,
                detail,
                null,
                null,
                "work_order",
                workOrderId
        ));
    }

    public void recordModelOperation(AuthorizationContext context, String eventType, String modelCode, String versionNo, String beforeState, String afterState, String sourceRecordId) {
        record(new AuditLogCommand(
                null,
                CATEGORY_MODEL,
                eventType,
                SOURCE_MODEL_GATEWAY,
                context.userId(),
                context.username(),
                null,
                null,
                OUTCOME_SUCCESS,
                "ROLLBACK".equals(eventType) || "MODEL_ROLLBACK".equals(eventType) ? "CRITICAL" : "HIGH",
                "MODEL",
                modelCode,
                eventType,
                null,
                null,
                null,
                null,
                null,
                versionNo == null ? "modelCode=" + modelCode : "modelCode=" + modelCode + ", versionNo=" + versionNo,
                beforeState,
                afterState,
                "model_operation_audit",
                sourceRecordId
        ));
    }

    public void recordFeatureViewOperation(AuthorizationContext context, String eventType, String objectType, String objectId, String outcome, String detail, String sourceRecordId) {
        record(new AuditLogCommand(
                null,
                CATEGORY_FEATURE_VIEW,
                eventType,
                SOURCE_FEATURE_VIEW,
                context.userId(),
                context.username(),
                null,
                null,
                outcome,
                "HIGH",
                objectType,
                objectId,
                eventType,
                null,
                null,
                null,
                null,
                null,
                detail,
                null,
                null,
                sourceRecordId != null && sourceRecordId.startsWith("FVA-") ? "feature_view_access_audit" : "feature_view_grant",
                sourceRecordId
        ));
    }

    private PageResponse<AuditLogResponse> paginate(List<AuditLogResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        return PageResponse.of(items.subList(fromIndex, Math.min(fromIndex + safePageSize, items.size())), items.size(), safePage, safePageSize);
    }

    private AuditLogResponse toResponse(AuditLogEntity audit) {
        return new AuditLogResponse(
                audit.getAuditId(),
                audit.getEventTime(),
                audit.getEventCategory(),
                audit.getEventType(),
                audit.getSourceModule(),
                audit.getActorUserId(),
                audit.getActorUsername(),
                audit.getAuthMode(),
                audit.getEmergencyAccountId(),
                audit.getOutcome(),
                audit.getRiskLevel(),
                audit.getObjectType(),
                audit.getObjectId(),
                audit.getAction(),
                audit.getHttpMethod(),
                audit.getRequestPath(),
                audit.getClientIp(),
                audit.getUserAgent(),
                audit.getTraceId(),
                audit.getDetail(),
                audit.getBeforeSnapshot(),
                audit.getAfterSnapshot(),
                audit.getSourceTable(),
                audit.getSourceRecordId()
        );
    }

    private boolean isEmergencyAudit(AuditLogEntity audit) {
        return AuthMode.BREAK_GLASS.equalsIgnoreCase(nullToBlank(audit.getAuthMode()))
                || StringUtils.hasText(audit.getEmergencyAccountId())
                || CATEGORY_BREAK_GLASS.equalsIgnoreCase(nullToBlank(audit.getEventCategory()));
    }

    private boolean isBreakGlass(String eventType, String authMode, String emergencyAccountId) {
        return AuthMode.BREAK_GLASS.equalsIgnoreCase(nullToBlank(authMode))
                || StringUtils.hasText(emergencyAccountId)
                || nullToBlank(eventType).startsWith("BREAK_GLASS_");
    }

    private String resolveRisk(String eventType) {
        String normalized = nullToBlank(eventType);
        if (normalized.contains("DISABLED") || normalized.contains("REVOKED") || normalized.contains("PERMISSION") || normalized.contains("BREAK_GLASS")) {
            return "CRITICAL";
        }
        if (normalized.contains("LOGIN") || normalized.contains("TOKEN") || normalized.contains("OIDC")) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || Objects.equals(normalize(actual), normalize(expected));
    }

    private boolean inRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (value == null) {
            return false;
        }
        return (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private PrincipalSnapshot currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new PrincipalSnapshot(null, null, null, null);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LocalAccessTokenService.AccessTokenPrincipal accessTokenPrincipal) {
            return new PrincipalSnapshot(
                    accessTokenPrincipal.userId(),
                    accessTokenPrincipal.username(),
                    accessTokenPrincipal.authMode(),
                    accessTokenPrincipal.emergencyAccountId()
            );
        }
        if (principal instanceof OidcUser oidcUser) {
            return new PrincipalSnapshot(oidcUser.getSubject(), oidcUser.getPreferredUsername(), AuthMode.OIDC, null);
        }
        if (principal instanceof Jwt jwt) {
            return new PrincipalSnapshot(jwt.getSubject(), String.valueOf(jwt.getClaims().getOrDefault("preferred_username", jwt.getSubject())), AuthMode.OIDC, null);
        }
        return new PrincipalSnapshot(authentication.getName(), authentication.getName(), AuthMode.STANDARD, null);
    }

    private HttpSnapshot currentHttp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return new HttpSnapshot(
                    request.getMethod(),
                    request.getRequestURI(),
                    clientIp(request),
                    request.getHeader("User-Agent")
            );
        }
        return new HttpSnapshot(null, null, null, null);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record PrincipalSnapshot(String userId, String username, String authMode, String emergencyAccountId) {
    }

    private record HttpSnapshot(String method, String path, String clientIp, String userAgent) {
    }

    public record AuditLogCommand(
            LocalDateTime eventTime,
            String eventCategory,
            String eventType,
            String sourceModule,
            String actorUserId,
            String actorUsername,
            String authMode,
            String emergencyAccountId,
            String outcome,
            String riskLevel,
            String objectType,
            String objectId,
            String action,
            String httpMethod,
            String requestPath,
            String clientIp,
            String userAgent,
            String traceId,
            String detail,
            String beforeSnapshot,
            String afterSnapshot,
            String sourceTable,
            String sourceRecordId
    ) {
    }
}
