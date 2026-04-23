package com.uscdip.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({BackendOidcProperties.class, GatewayProperties.class})
public class SecurityConfig {

    private static final String[] DOCUMENTATION_ENDPOINTS = {
            "/h2-console/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    private static final String[] AUTH_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/login-url",
            "/api/auth/callback",
            "/api/auth/refresh",
            "/api/auth/emergency/login",
            "/ws/push"
    };

    private static final String[] PUBLIC_SPEC_ENDPOINTS = {
            "/api/menu-boundaries",
            "/api/platforms",
            "/api/platforms/*",
            "/api/gis/field-spec",
            "/api/gis/convert",
            "/api/gis/depth/validate",
            "/api/authz/matrix-spec",
            "/api/authz/matrix"
    };

    @Bean
    LocalAccessTokenAuthenticationFilter localAccessTokenAuthenticationFilter(
            com.uscdip.backend.service.LocalAccessTokenService localAccessTokenService,
            com.uscdip.backend.service.TokenRevocationService tokenRevocationService,
            org.springframework.beans.factory.ObjectProvider<JwtDecoder> jwtDecoderProvider
    ) {
        return new LocalAccessTokenAuthenticationFilter(localAccessTokenService, tokenRevocationService, jwtDecoderProvider);
    }

    @Bean
    PasswordEncoder passwordEncoder(BackendOidcProperties oidcProperties) {
        return new BCryptPasswordEncoder(oidcProperties.getEmergencyPasswordHashStrength());
    }

    @Bean
    GatewayControlFilter gatewayControlFilter(
            com.uscdip.backend.service.GatewayPolicyService gatewayPolicyService,
            com.uscdip.backend.service.GatewayRateLimitService gatewayRateLimitService,
            GatewayProperties gatewayProperties,
            ObjectMapper objectMapper
    ) {
        return new GatewayControlFilter(gatewayPolicyService, gatewayRateLimitService, gatewayProperties, objectMapper);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "backend.oidc", name = "enabled", havingValue = "true")
    static class OidcEnabledSecurityConfiguration {

        @Bean
        SecurityFilterChain oidcSecurityFilterChain(
                HttpSecurity http,
                ObjectMapper objectMapper,
                LocalAccessTokenAuthenticationFilter localAccessTokenAuthenticationFilter,
                GatewayControlFilter gatewayControlFilter
        ) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(DOCUMENTATION_ENDPOINTS).permitAll()
                            .requestMatchers(AUTH_ENDPOINTS).permitAll()
                            .requestMatchers(PUBLIC_SPEC_ENDPOINTS).permitAll()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(localAccessTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(gatewayControlFilter, LocalAccessTokenAuthenticationFilter.class)
                    .exceptionHandling(ex -> configureExceptionHandling(ex, objectMapper));

            return http.build();
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository(BackendOidcProperties oidcProperties) {
            return new InMemoryClientRegistrationRepository(buildClientRegistration(oidcProperties));
        }

        @Bean
        JwtDecoder jwtDecoder(BackendOidcProperties oidcProperties) {
            return NimbusJwtDecoder.withJwkSetUri(oidcProperties.getIssuerUri() + "/protocol/openid-connect/certs").build();
        }

        private ClientRegistration buildClientRegistration(BackendOidcProperties oidcProperties) {
            String issuerUri = oidcProperties.getIssuerUri();
            return ClientRegistration.withRegistrationId(oidcProperties.getRegistrationId())
                    .registrationId(oidcProperties.getRegistrationId())
                    .clientId(oidcProperties.getClientId())
                    .clientSecret(oidcProperties.getClientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri(issuerUri + "/protocol/openid-connect/auth")
                    .tokenUri(issuerUri + "/protocol/openid-connect/token")
                    .jwkSetUri(issuerUri + "/protocol/openid-connect/certs")
                    .issuerUri(issuerUri)
                    .userNameAttributeName("sub")
                    .providerConfigurationMetadata(java.util.Map.of(
                            "end_session_endpoint", issuerUri + "/protocol/openid-connect/logout"
                    ))
                    .scope(List.of("openid", "profile", "email"))
                    .clientName(oidcProperties.getRegistrationId())
                    .build();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "backend.oidc", name = "enabled", havingValue = "false", matchIfMissing = true)
    static class OidcDisabledSecurityConfiguration {

        @Bean
        SecurityFilterChain localSecurityFilterChain(
                HttpSecurity http,
                ObjectMapper objectMapper,
                LocalAccessTokenAuthenticationFilter localAccessTokenAuthenticationFilter,
                GatewayControlFilter gatewayControlFilter
        ) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(DOCUMENTATION_ENDPOINTS).permitAll()
                            .requestMatchers(AUTH_ENDPOINTS).permitAll()
                            .requestMatchers("/api/auth/me", "/api/auth/logout").permitAll()
                            .requestMatchers(PUBLIC_SPEC_ENDPOINTS).permitAll()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(localAccessTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(gatewayControlFilter, LocalAccessTokenAuthenticationFilter.class)
                    .exceptionHandling(ex -> configureExceptionHandling(ex, objectMapper));

            return http.build();
        }
    }

    private static void configureExceptionHandling(
            org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer<HttpSecurity> exceptionHandling,
            ObjectMapper objectMapper
    ) {
        exceptionHandling
                .authenticationEntryPoint((request, response, authException) -> writeError(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        ApiResponse.failure(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage()),
                        objectMapper
                ))
                .accessDeniedHandler((request, response, accessDeniedException) -> writeError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        ApiResponse.failure(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage()),
                        objectMapper
                ));
    }

    private static void writeError(HttpServletResponse response, int status, ApiResponse<?> payload, ObjectMapper objectMapper)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), payload);
    }
}
