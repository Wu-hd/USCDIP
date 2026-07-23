package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.TokenRevocationResponse;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.AuthorizationService;
import com.uscdip.backend.service.LocalAccessTokenService;
import com.uscdip.backend.service.OidcUserSyncService;
import com.uscdip.backend.service.TokenRevocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/admin/users")
@Tag(name = "Auth Admin", description = "B-04 Token 吊销与权限收敛管理接口")
public class AuthAdminController {

    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final OidcUserSyncService oidcUserSyncService;
    private final AuthorizationService authorizationService;
    private final TokenRevocationService tokenRevocationService;

    public AuthAdminController(
            OidcUserSyncService oidcUserSyncService,
            AuthorizationService authorizationService,
            TokenRevocationService tokenRevocationService
    ) {
        this.oidcUserSyncService = oidcUserSyncService;
        this.authorizationService = authorizationService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @PostMapping("/{userId}/disable")
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT")
    @Operation(summary = "禁用用户并吊销全部会话")
    public ApiResponse<TokenRevocationResponse> disableUser(
            @Parameter(description = "用户ID", required = true) @PathVariable String userId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        String actorUserId = requirePlatformAdmin(authentication);
        TokenRevocationResponse response = tokenRevocationService.disableUser(
                userId,
                actorUserId,
                resolveClientIp(request),
                request.getHeader("User-Agent")
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/{userId}/permissions/revoke")
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT")
    @Operation(summary = "触发用户权限重大变更后的全量收敛")
    public ApiResponse<TokenRevocationResponse> revokePermissions(
            @Parameter(description = "用户ID", required = true) @PathVariable String userId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        String actorUserId = requirePlatformAdmin(authentication);
        TokenRevocationResponse response = tokenRevocationService.revokeUserPermissions(
                userId,
                actorUserId,
                resolveClientIp(request),
                request.getHeader("User-Agent")
        );
        return ApiResponse.success(response);
    }

    private String requirePlatformAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
        }

        String userId = resolveUserId(authentication);
        if (userId == null) {
            throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
        }

        Map<String, Object> snapshot = authorizationService.getUserSnapshot(userId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + userId));
        Object roleCodesObject = snapshot.get("roleCodes");
        boolean isPlatformAdmin = roleCodesObject instanceof List<?> roleCodes
                && roleCodes.stream().anyMatch(role -> ROLE_PLATFORM_ADMIN.equalsIgnoreCase(String.valueOf(role)));
        if (!isPlatformAdmin) {
            throw new AuthFlowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "PLATFORM_ADMIN role required");
        }
        return userId;
    }

    private String resolveUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof LocalAccessTokenService.AccessTokenPrincipal accessTokenPrincipal) {
            return accessTokenPrincipal.userId();
        }
        if (principal instanceof OidcUser oidcUser) {
            return oidcUserSyncService.syncOidcUser(oidcUser);
        }
        if (principal instanceof Jwt jwt) {
            return oidcUserSyncService.syncJwtClaims(jwt.getClaims());
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
