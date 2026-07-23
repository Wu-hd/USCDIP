package com.uscdip.backend.config;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.AuthorizationService;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.LocalAccessTokenService;
import com.uscdip.backend.service.UnifiedAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
public class AuthzGuardAspect {

    private final CurrentUserResolver currentUserResolver;
    private final AuthorizationService authorizationService;
    private final UnifiedAuditService unifiedAuditService;

    public AuthzGuardAspect(CurrentUserResolver currentUserResolver, AuthorizationService authorizationService, UnifiedAuditService unifiedAuditService) {
        this.currentUserResolver = currentUserResolver;
        this.authorizationService = authorizationService;
        this.unifiedAuditService = unifiedAuditService;
    }

    @Around("@within(com.uscdip.backend.annotation.AuthzGuard) || @annotation(com.uscdip.backend.annotation.AuthzGuard)")
    public Object enforceGuard(ProceedingJoinPoint joinPoint) throws Throwable {
        AuthzGuard guard = resolveGuard(joinPoint);
        if (guard == null) {
            return joinPoint.proceed();
        }

        AuthorizationContext context = currentUserResolver.requireCurrentContext();
        LocalAccessTokenService.AccessTokenPrincipal principal = currentLocalPrincipal();
        boolean breakGlass = principal != null && AuthMode.BREAK_GLASS.equals(principal.authMode());
        if (StringUtils.hasText(guard.requiredRole()) && !context.roleCodes().contains(guard.requiredRole().trim().toUpperCase())) {
            recordBreakGlass(joinPoint, guard, principal, "DENY", "Required role is missing: " + guard.requiredRole());
            throw new AuthFlowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Required role is missing: " + guard.requiredRole());
        }
        if (StringUtils.hasText(guard.entryPermission()) && !authorizationService.hasPermission(context, guard.entryPermission())) {
            recordBreakGlass(joinPoint, guard, principal, "DENY", ErrorCode.ENTRY_PERMISSION_DENIED.defaultMessage());
            throw new AuthFlowException(
                    ErrorCode.ENTRY_PERMISSION_DENIED,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.ENTRY_PERMISSION_DENIED.defaultMessage()
            );
        }
        if (StringUtils.hasText(guard.menuPermission()) && !authorizationService.hasPermission(context, guard.menuPermission())) {
            recordBreakGlass(joinPoint, guard, principal, "DENY", ErrorCode.MENU_PERMISSION_DENIED.defaultMessage());
            throw new AuthFlowException(
                    ErrorCode.MENU_PERMISSION_DENIED,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.MENU_PERMISSION_DENIED.defaultMessage()
            );
        }
        try {
            Object result = joinPoint.proceed();
            if (breakGlass) {
                recordBreakGlass(joinPoint, guard, principal, "SUCCESS", null);
            }
            return result;
        } catch (Throwable ex) {
            if (breakGlass) {
                recordBreakGlass(joinPoint, guard, principal, "ERROR", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            throw ex;
        }
    }

    private AuthzGuard resolveGuard(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuthzGuard methodGuard = method.getAnnotation(AuthzGuard.class);
        if (methodGuard != null) {
            return methodGuard;
        }
        return method.getDeclaringClass().getAnnotation(AuthzGuard.class);
    }

    private LocalAccessTokenService.AccessTokenPrincipal currentLocalPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof LocalAccessTokenService.AccessTokenPrincipal accessTokenPrincipal
                ? accessTokenPrincipal
                : null;
    }

    private void recordBreakGlass(
            ProceedingJoinPoint joinPoint,
            AuthzGuard guard,
            LocalAccessTokenService.AccessTokenPrincipal principal,
            String outcome,
            String reason
    ) {
        if (principal == null || !AuthMode.BREAK_GLASS.equals(principal.authMode())) {
            return;
        }
        HttpServletRequest request = currentRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String detail = "method=" + signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName()
                + ", requiredRole=" + guard.requiredRole()
                + ", entryPermission=" + guard.entryPermission()
                + ", menuPermission=" + guard.menuPermission()
                + (reason == null ? "" : ", reason=" + reason);
        unifiedAuditService.record(new UnifiedAuditService.AuditLogCommand(
                null,
                UnifiedAuditService.CATEGORY_BREAK_GLASS,
                "BREAK_GLASS_OPERATION",
                UnifiedAuditService.SOURCE_AUTHZ_GUARD,
                principal.userId(),
                principal.username(),
                principal.authMode(),
                principal.emergencyAccountId(),
                outcome,
                "CRITICAL",
                "HTTP_ENDPOINT",
                request == null ? signature.getMethod().getName() : request.getRequestURI(),
                "BREAK_GLASS_OPERATION",
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI(),
                request == null ? null : clientIp(request),
                request == null ? null : request.getHeader("User-Agent"),
                null,
                detail,
                null,
                null,
                "authz_guard",
                principal.tokenId()
        ));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
