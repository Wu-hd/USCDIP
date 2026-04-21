package com.uscdip.backend.controller;

import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.dto.AuthCallbackRequest;
import com.uscdip.backend.dto.AuthLoginDescriptor;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.dto.TokenRevocationResponse;
import com.uscdip.backend.dto.TokenRefreshRequest;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.AuthorizationService;
import com.uscdip.backend.service.LocalAccessTokenService;
import com.uscdip.backend.service.LocalTokenService;
import com.uscdip.backend.service.OidcAuthorizationService;
import com.uscdip.backend.service.OidcUserSyncService;
import com.uscdip.backend.service.TokenRevocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "B-02 OIDC 登录与登出接口")
public class AuthController {

    private final BackendOidcProperties oidcProperties;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;
    private final OidcUserSyncService oidcUserSyncService;
    private final AuthorizationService authorizationService;
    private final OidcAuthorizationService oidcAuthorizationService;
    private final LocalTokenService localTokenService;
    private final TokenRevocationService tokenRevocationService;

    public AuthController(
            BackendOidcProperties oidcProperties,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            OidcUserSyncService oidcUserSyncService,
            AuthorizationService authorizationService,
            OidcAuthorizationService oidcAuthorizationService,
            LocalTokenService localTokenService,
            TokenRevocationService tokenRevocationService
    ) {
        this.oidcProperties = oidcProperties;
        this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
        this.oidcUserSyncService = oidcUserSyncService;
        this.authorizationService = authorizationService;
        this.oidcAuthorizationService = oidcAuthorizationService;
        this.localTokenService = localTokenService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @GetMapping("/login")
    @Operation(summary = "获取 OIDC 登录入口")
    public ApiResponse<AuthLoginDescriptor> getLogin(
            @RequestParam(required = false) String redirectUri,
            HttpServletRequest request
    ) {
        return ApiResponse.success(oidcAuthorizationService.createLoginDescriptor(
                redirectUri,
                resolveClientIp(request),
                request.getHeader("User-Agent")
        ));
    }

    @GetMapping("/login-url")
    @Operation(summary = "获取 OIDC 登录入口")
    public ApiResponse<AuthLoginDescriptor> getLoginUrl(
            @RequestParam(required = false) String redirectUri,
            HttpServletRequest request
    ) {
        return ApiResponse.success(oidcAuthorizationService.createLoginDescriptor(
                redirectUri,
                resolveClientIp(request),
                request.getHeader("User-Agent")
        ));
    }

    @PostMapping("/callback")
    @Operation(summary = "OIDC 回调换取本地 access/refresh token")
    public ApiResponse<TokenPairResponse> callback(
            @Valid @RequestBody AuthCallbackRequest request,
            HttpServletRequest httpServletRequest
    ) {
        TokenPairResponse tokenPairResponse = oidcAuthorizationService.handleCallback(
                request,
                resolveClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(tokenPairResponse);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新本地 access/refresh token")
    public ApiResponse<TokenPairResponse> refresh(
            @Valid @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpServletRequest
    ) {
        TokenPairResponse tokenPairResponse = localTokenService.refresh(
                request.refreshToken(),
                resolveClientIp(httpServletRequest),
                httpServletRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(tokenPairResponse);
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户快照")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(Authentication authentication, HttpServletRequest request) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LocalAccessTokenService.AccessTokenPrincipal) {
            return buildLocalSnapshotResponse(authentication);
        }
        if (hasBearerToken(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage()));
        }
        if (!oidcProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure(ErrorCode.OIDC_DISABLED, ErrorCode.OIDC_DISABLED.defaultMessage()));
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage()));
        }

        String userId;
        try {
            userId = resolveOrSyncUserId(authentication);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(ErrorCode.OIDC_USER_SYNC_FAILED, ex.getMessage()));
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(ErrorCode.OIDC_LOGIN_REQUIRED, ErrorCode.OIDC_LOGIN_REQUIRED.defaultMessage()));
        }

        Optional<Map<String, Object>> snapshot = authorizationService.getUserSnapshot(userId);
        if (snapshot.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure(ErrorCode.USER_NOT_FOUND, "User not found after OIDC synchronization: " + userId));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("authenticationType", authentication.getClass().getSimpleName());
        payload.put("snapshot", snapshot.get());
        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    @PostMapping("/logout")
    @Operation(summary = "执行统一登出并返回 OIDC 提供方退出地址")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LocalAccessTokenService.AccessTokenPrincipal accessTokenPrincipal) {
            try {
                TokenRevocationResponse revocation = tokenRevocationService.revokeCurrentSession(
                        accessTokenPrincipal.userId(),
                        accessTokenPrincipal.sessionId(),
                        resolveClientIp(request),
                        request.getHeader("User-Agent")
                );
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                SecurityContextHolder.clearContext();

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("loggedOut", true);
                payload.put("providerLogoutUrl", null);
                payload.put("postLogoutRedirectUri", null);
                payload.put("revokedSessionCount", revocation.revokedSessionCount());
                payload.put("revokedRefreshTokenCount", revocation.revokedRefreshTokenCount());
                payload.put("authMode", accessTokenPrincipal.authMode());
                return ResponseEntity.ok(ApiResponse.success(payload));
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.failure(ErrorCode.OIDC_LOGOUT_FAILED, ex.getMessage()));
            }
        }
        if (hasBearerToken(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage()));
        }
        if (!oidcProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure(ErrorCode.OIDC_DISABLED, ErrorCode.OIDC_DISABLED.defaultMessage()));
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage()));
        }

        String providerLogoutUrl;
        try {
            String currentUserId = resolveOrSyncUserId(authentication);
            String sessionId = resolveSessionId(authentication);
            providerLogoutUrl = buildProviderLogoutUrl(authentication);
            TokenRevocationResponse revocation = tokenRevocationService.revokeCurrentSession(
                    currentUserId,
                    sessionId,
                    resolveClientIp(request),
                    request.getHeader("User-Agent")
            );
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            SecurityContextHolder.clearContext();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("loggedOut", true);
            payload.put("providerLogoutUrl", providerLogoutUrl);
            payload.put("postLogoutRedirectUri", oidcProperties.getPostLogoutRedirectUri());
            payload.put("revokedSessionCount", revocation.revokedSessionCount());
            payload.put("revokedRefreshTokenCount", revocation.revokedRefreshTokenCount());
            payload.put("authMode", AuthMode.OIDC);
            return ResponseEntity.ok(ApiResponse.success(payload));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(ErrorCode.OIDC_LOGOUT_FAILED, ex.getMessage()));
        }
    }

    private String resolveOrSyncUserId(Authentication authentication) {
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

    private String resolveSessionId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof LocalAccessTokenService.AccessTokenPrincipal accessTokenPrincipal) {
            return accessTokenPrincipal.sessionId();
        }
        return null;
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildLocalSnapshotResponse(Authentication authentication) {
        String userId = resolveOrSyncUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage()));
        }
        Optional<Map<String, Object>> snapshot = authorizationService.getUserSnapshot(userId);
        if (snapshot.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("authenticationType", authentication.getClass().getSimpleName());
        payload.put("snapshot", snapshot.get());
        if (authentication.getPrincipal() instanceof LocalAccessTokenService.AccessTokenPrincipal accessTokenPrincipal) {
            payload.put("authMode", accessTokenPrincipal.authMode());
        }
        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    private boolean hasBearerToken(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ");
    }

    private String buildProviderLogoutUrl(Authentication authentication) {
        ClientRegistrationRepository clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
        ClientRegistration registration = clientRegistrationRepository == null
                ? null
                : clientRegistrationRepository.findByRegistrationId(oidcProperties.getRegistrationId());
        String endSessionEndpoint = null;
        if (registration != null) {
            Object configuredEndpoint = registration.getProviderDetails()
                    .getConfigurationMetadata()
                    .get("end_session_endpoint");
            if (configuredEndpoint instanceof String endpoint && !endpoint.isBlank()) {
                endSessionEndpoint = endpoint;
            }
        }
        if (endSessionEndpoint == null || endSessionEndpoint.isBlank()) {
            endSessionEndpoint = oidcProperties.getIssuerUri() + "/protocol/openid-connect/logout";
        }

        String redirect = URLEncoder.encode(oidcProperties.getPostLogoutRedirectUri(), StandardCharsets.UTF_8);
        StringBuilder logoutUrl = new StringBuilder(endSessionEndpoint)
                .append("?post_logout_redirect_uri=")
                .append(redirect);

        if (authentication.getPrincipal() instanceof OidcUser oidcUser && oidcUser.getIdToken() != null) {
            String idTokenHint = URLEncoder.encode(oidcUser.getIdToken().getTokenValue(), StandardCharsets.UTF_8);
            logoutUrl.append("&id_token_hint=").append(idTokenHint);
        }
        return logoutUrl.toString();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
