package com.uscdip.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.OidcUserSyncService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(BackendOidcProperties.class)
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
            "/api/auth/login-url"
    };

    private static final String[] DEMO_ENDPOINTS = {
            "/api/menu-boundaries",
            "/api/platforms",
            "/api/platforms/*",
            "/api/object-dictionary",
            "/api/object-dictionary/page",
            "/api/object-chain/**",
            "/api/gis/field-spec",
            "/api/gis/convert",
            "/api/gis/depth/validate",
            "/api/authz/**"
    };

    @Configuration
    @ConditionalOnProperty(prefix = "backend.oidc", name = "enabled", havingValue = "true")
    static class OidcEnabledSecurityConfiguration {

        @Bean
        SecurityFilterChain oidcSecurityFilterChain(
                HttpSecurity http,
                ObjectMapper objectMapper,
                OidcUserSyncService oidcUserSyncService,
                OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver
        ) throws Exception {
            SavedRequestAwareAuthenticationSuccessHandler delegateSuccessHandler = new SavedRequestAwareAuthenticationSuccessHandler();

            http
                    .csrf(csrf -> csrf.disable())
                    .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(DOCUMENTATION_ENDPOINTS).permitAll()
                            .requestMatchers(AUTH_ENDPOINTS).permitAll()
                            .requestMatchers(DEMO_ENDPOINTS).permitAll()
                            .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .oauth2Login(oauth2 -> oauth2
                            .authorizationEndpoint(endpoint -> endpoint
                                    .authorizationRequestResolver(pkceAuthorizationRequestResolver)
                            )
                            .successHandler((request, response, authentication) -> {
                                if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
                                    oidcUserSyncService.syncOidcUser(oidcUser);
                                }
                                delegateSuccessHandler.onAuthenticationSuccess(request, response, authentication);
                            })
                    )
                    .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                    .exceptionHandling(ex -> configureExceptionHandling(ex, objectMapper));

            return http.build();
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository(BackendOidcProperties oidcProperties) {
            return new InMemoryClientRegistrationRepository(buildClientRegistration(oidcProperties));
        }

        @Bean
        OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
            return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        }

        @Bean
        JwtDecoder jwtDecoder(BackendOidcProperties oidcProperties) {
            return JwtDecoders.fromIssuerLocation(oidcProperties.getIssuerUri());
        }

        @Bean
        OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
                ClientRegistrationRepository clientRegistrationRepository
        ) {
            DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                    clientRegistrationRepository,
                    OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
            );
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            return resolver;
        }

        private ClientRegistration buildClientRegistration(BackendOidcProperties oidcProperties) {
            return ClientRegistrations.fromIssuerLocation(oidcProperties.getIssuerUri())
                    .registrationId(oidcProperties.getRegistrationId())
                    .clientId(oidcProperties.getClientId())
                    .clientSecret(oidcProperties.getClientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope(List.of("openid", "profile", "email"))
                    .build();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "backend.oidc", name = "enabled", havingValue = "false", matchIfMissing = true)
    static class OidcDisabledSecurityConfiguration {

        @Bean
        SecurityFilterChain localSecurityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(DOCUMENTATION_ENDPOINTS).permitAll()
                            .requestMatchers(AUTH_ENDPOINTS).permitAll()
                            .requestMatchers("/api/auth/me", "/api/auth/logout").permitAll()
                            .requestMatchers(DEMO_ENDPOINTS).permitAll()
                            .anyRequest().authenticated()
                    )
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
