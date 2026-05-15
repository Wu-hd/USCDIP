package com.uscdip.backend.service;

import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserResolver {

    private final AuthorizationService authorizationService;
    private final OidcUserSyncService oidcUserSyncService;

    public CurrentUserResolver(AuthorizationService authorizationService, OidcUserSyncService oidcUserSyncService) {
        this.authorizationService = authorizationService;
        this.oidcUserSyncService = oidcUserSyncService;
    }

    public AuthorizationContext requireCurrentContext() {
        return requireContext(SecurityContextHolder.getContext().getAuthentication());
    }

    public AuthorizationContext requireContext(Authentication authentication) {
        String userId = resolveUserId(authentication);
        if (userId == null || userId.isBlank()) {
            throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
        }
        return authorizationService.getAuthorizationContext(userId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    public AuthorizationContext requireSelfOrPlatformAdmin(Authentication authentication, String targetUserId) {
        AuthorizationContext callerContext = requireContext(authentication);
        if (callerContext.platformAdmin() || callerContext.userId().equalsIgnoreCase(targetUserId)) {
            return authorizationService.getAuthorizationContext(targetUserId)
                    .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + targetUserId));
        }
        throw new AuthFlowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Self access or PLATFORM_ADMIN role required");
    }

    public String resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
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
}
