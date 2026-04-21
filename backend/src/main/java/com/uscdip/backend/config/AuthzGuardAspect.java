package com.uscdip.backend.config;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.AuthorizationService;
import com.uscdip.backend.service.CurrentUserResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

@Aspect
@Component
public class AuthzGuardAspect {

    private final CurrentUserResolver currentUserResolver;
    private final AuthorizationService authorizationService;

    public AuthzGuardAspect(CurrentUserResolver currentUserResolver, AuthorizationService authorizationService) {
        this.currentUserResolver = currentUserResolver;
        this.authorizationService = authorizationService;
    }

    @Around("@within(com.uscdip.backend.annotation.AuthzGuard) || @annotation(com.uscdip.backend.annotation.AuthzGuard)")
    public Object enforceGuard(ProceedingJoinPoint joinPoint) throws Throwable {
        AuthzGuard guard = resolveGuard(joinPoint);
        if (guard == null) {
            return joinPoint.proceed();
        }

        AuthorizationContext context = currentUserResolver.requireCurrentContext();
        if (StringUtils.hasText(guard.requiredRole()) && !context.roleCodes().contains(guard.requiredRole().trim().toUpperCase())) {
            throw new AuthFlowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Required role is missing: " + guard.requiredRole());
        }
        if (StringUtils.hasText(guard.entryPermission()) && !authorizationService.hasPermission(context, guard.entryPermission())) {
            throw new AuthFlowException(
                    ErrorCode.ENTRY_PERMISSION_DENIED,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.ENTRY_PERMISSION_DENIED.defaultMessage()
            );
        }
        if (StringUtils.hasText(guard.menuPermission()) && !authorizationService.hasPermission(context, guard.menuPermission())) {
            throw new AuthFlowException(
                    ErrorCode.MENU_PERMISSION_DENIED,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.MENU_PERMISSION_DENIED.defaultMessage()
            );
        }
        return joinPoint.proceed();
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
}
