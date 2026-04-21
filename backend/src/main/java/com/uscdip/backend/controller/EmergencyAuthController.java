package com.uscdip.backend.controller;

import com.uscdip.backend.dto.EmergencyAccountActivateRequest;
import com.uscdip.backend.dto.EmergencyAccountResponse;
import com.uscdip.backend.dto.EmergencyAccountRevokeRequest;
import com.uscdip.backend.dto.EmergencyAuditPageResponse;
import com.uscdip.backend.dto.EmergencyLoginRequest;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.AuthorizationService;
import com.uscdip.backend.service.EmergencyAccessService;
import com.uscdip.backend.service.LocalAccessTokenService;
import com.uscdip.backend.service.OidcUserSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/emergency")
@Tag(name = "Emergency Auth", description = "B-05 应急旁路账号与审计接口")
public class EmergencyAuthController {

    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final EmergencyAccessService emergencyAccessService;
    private final OidcUserSyncService oidcUserSyncService;
    private final AuthorizationService authorizationService;

    public EmergencyAuthController(
            EmergencyAccessService emergencyAccessService,
            OidcUserSyncService oidcUserSyncService,
            AuthorizationService authorizationService
    ) {
        this.emergencyAccessService = emergencyAccessService;
        this.oidcUserSyncService = oidcUserSyncService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/login")
    @Operation(summary = "应急旁路账号登录")
    public ApiResponse<TokenPairResponse> login(
            @Valid @RequestBody EmergencyLoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(emergencyAccessService.login(
                request.username(),
                request.password(),
                resolveClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/accounts/{accountId}/activate")
    @Operation(summary = "激活应急旁路账号")
    public ApiResponse<EmergencyAccountResponse> activate(
            @Parameter(description = "旁路账号ID", required = true) @PathVariable String accountId,
            @Valid @RequestBody EmergencyAccountActivateRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        String actorUserId = requirePlatformAdmin(authentication);
        return ApiResponse.success(emergencyAccessService.activate(
                accountId,
                request,
                actorUserId,
                resolveClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/accounts/{accountId}/revoke")
    @Operation(summary = "撤销应急旁路账号")
    public ApiResponse<EmergencyAccountResponse> revoke(
            @Parameter(description = "旁路账号ID", required = true) @PathVariable String accountId,
            @Valid @RequestBody EmergencyAccountRevokeRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        String actorUserId = requirePlatformAdmin(authentication);
        return ApiResponse.success(emergencyAccessService.revoke(
                accountId,
                request,
                actorUserId,
                resolveClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    @GetMapping("/audit")
    @Operation(summary = "查询应急旁路审计记录")
    public ApiResponse<EmergencyAuditPageResponse> queryAudit(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication authentication
    ) {
        requirePlatformAdmin(authentication);
        return ApiResponse.success(emergencyAccessService.queryAudit(from, to, username, outcome, page, pageSize));
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
