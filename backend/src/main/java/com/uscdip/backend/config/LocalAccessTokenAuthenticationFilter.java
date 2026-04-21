package com.uscdip.backend.config;

import com.uscdip.backend.service.LocalAccessTokenService;
import com.uscdip.backend.service.TokenRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

public class LocalAccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private final LocalAccessTokenService localAccessTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final ObjectProvider<JwtDecoder> oidcJwtDecoderProvider;

    public LocalAccessTokenAuthenticationFilter(
            LocalAccessTokenService localAccessTokenService,
            TokenRevocationService tokenRevocationService,
            ObjectProvider<JwtDecoder> oidcJwtDecoderProvider
    ) {
        this.localAccessTokenService = localAccessTokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.oidcJwtDecoderProvider = oidcJwtDecoderProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (!tryAuthenticateWithLocalToken(token, request)) {
                    tryAuthenticateWithOidcToken(token);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean tryAuthenticateWithLocalToken(String token, HttpServletRequest request) {
        try {
            LocalAccessTokenService.AccessTokenPrincipal principal = localAccessTokenService.verify(token);
            tokenRevocationService.validateAccessToken(
                    principal,
                    nullSafeClientIp(request),
                    request.getHeader("User-Agent")
            );
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    token,
                    Collections.emptyList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void tryAuthenticateWithOidcToken(String token) {
        try {
            JwtDecoder oidcJwtDecoder = oidcJwtDecoderProvider.getIfAvailable();
            if (oidcJwtDecoder == null) {
                return;
            }
            Jwt jwt = oidcJwtDecoder.decode(token);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    jwt,
                    token,
                    Collections.emptyList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ignored) {
            // Let the request continue unauthenticated; the security entry point will handle protected endpoints.
        }
    }

    private String nullSafeClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
