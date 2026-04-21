package com.uscdip.backend.controller;

import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.exception.GlobalExceptionHandler;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.AuthorizationService;
import com.uscdip.backend.service.LocalTokenService;
import com.uscdip.backend.service.OidcAuthorizationService;
import com.uscdip.backend.service.OidcUserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthCallbackControllerTest {

    private MockMvc mockMvc;
    private OidcAuthorizationService oidcAuthorizationService;

    @BeforeEach
    void setUp() {
        BackendOidcProperties oidcProperties = new BackendOidcProperties();
        oidcProperties.setEnabled(true);

        oidcAuthorizationService = mock(OidcAuthorizationService.class);
        AuthController authController = new AuthController(
                oidcProperties,
                new StaticListableBeanFactory().getBeanProvider(ClientRegistrationRepository.class),
                mock(OidcUserSyncService.class),
                mock(AuthorizationService.class),
                oidcAuthorizationService,
                mock(LocalTokenService.class)
        );

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void callbackReturnsTokenPairOnSuccess() throws Exception {
        when(oidcAuthorizationService.handleCallback(any(), any(), any())).thenReturn(new TokenPairResponse(
                "access-token",
                Instant.parse("2099-01-01T00:00:00Z"),
                "refresh-token",
                Instant.parse("2099-01-15T00:00:00Z"),
                "Bearer",
                Map.of("userId", "U-DISPATCH-001")
        ));

        mockMvc.perform(post("/api/auth/callback")
                        .contentType("application/json")
                        .content("""
                                {"code":"code-001","state":"state-001","redirectUri":"http://localhost:5173/auth/callback"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void callbackReturnsOidcStateInvalidOnFailure() throws Exception {
        when(oidcAuthorizationService.handleCallback(any(), any(), any())).thenThrow(
                new AuthFlowException(ErrorCode.OIDC_STATE_INVALID, HttpStatus.UNAUTHORIZED, ErrorCode.OIDC_STATE_INVALID.defaultMessage())
        );

        mockMvc.perform(post("/api/auth/callback")
                        .contentType("application/json")
                        .content("""
                                {"code":"code-001","state":"bad-state","redirectUri":"http://localhost:5173/auth/callback"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OIDC_STATE_INVALID"));
    }
}
