package com.uscdip.backend.controller;

import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.AuthorizationService;
import com.uscdip.backend.service.LocalTokenService;
import com.uscdip.backend.service.OidcAuthorizationService;
import com.uscdip.backend.service.OidcUserSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AuthControllerTest {

    @Test
    void logoutUsesMetadataEndSessionEndpointWhenPresent() {
        BackendOidcProperties oidcProperties = createEnabledProperties();
        ClientRegistrationRepository clientRegistrationRepository =
                new InMemoryClientRegistrationRepository(createClientRegistration("https://issuer.example/logout"));
        AuthController controller = new AuthController(
                oidcProperties,
                new StaticListableBeanFactory(Map.of("clientRegistrationRepository", clientRegistrationRepository))
                        .getBeanProvider(ClientRegistrationRepository.class),
                mock(OidcUserSyncService.class),
                mock(AuthorizationService.class),
                mock(OidcAuthorizationService.class),
                mock(LocalTokenService.class)
        );

        ApiResponse<Map<String, Object>> responseBody = controller.logout(
                authenticatedUser(),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()
        ).getBody();

        assertEquals(true, responseBody.success());
        String providerLogoutUrl = responseBody.data().get("providerLogoutUrl").toString();
        assertTrue(providerLogoutUrl.startsWith("https://issuer.example/logout?post_logout_redirect_uri="));
        assertTrue(providerLogoutUrl.contains("id_token_hint=id-token-value"));
    }

    @Test
    void logoutFallsBackToIssuerDefaultEndpointWhenMetadataIsMissing() {
        BackendOidcProperties oidcProperties = createEnabledProperties();
        ClientRegistrationRepository clientRegistrationRepository =
                new InMemoryClientRegistrationRepository(createClientRegistration(null));
        AuthController controller = new AuthController(
                oidcProperties,
                new StaticListableBeanFactory(Map.of("clientRegistrationRepository", clientRegistrationRepository))
                        .getBeanProvider(ClientRegistrationRepository.class),
                mock(OidcUserSyncService.class),
                mock(AuthorizationService.class),
                mock(OidcAuthorizationService.class),
                mock(LocalTokenService.class)
        );

        ApiResponse<Map<String, Object>> responseBody = controller.logout(
                authenticatedUser(),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()
        ).getBody();

        assertEquals(true, responseBody.success());
        String providerLogoutUrl = responseBody.data().get("providerLogoutUrl").toString();
        assertTrue(providerLogoutUrl.startsWith(
                "https://issuer.example/realms/uscdip/protocol/openid-connect/logout?post_logout_redirect_uri="
        ));
    }

    private BackendOidcProperties createEnabledProperties() {
        BackendOidcProperties oidcProperties = new BackendOidcProperties();
        oidcProperties.setEnabled(true);
        oidcProperties.setRegistrationId("keycloak");
        oidcProperties.setIssuerUri("https://issuer.example/realms/uscdip");
        oidcProperties.setPostLogoutRedirectUri("http://localhost:8080/swagger-ui/index.html");
        return oidcProperties;
    }

    private ClientRegistration createClientRegistration(String endSessionEndpoint) {
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId("keycloak")
                .clientId("uscdip-backend")
                .clientSecret("change-me")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://issuer.example/auth")
                .tokenUri("https://issuer.example/token")
                .jwkSetUri("https://issuer.example/jwks")
                .issuerUri("https://issuer.example/realms/uscdip")
                .userInfoUri("https://issuer.example/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("keycloak");

        if (endSessionEndpoint != null) {
            builder.providerConfigurationMetadata(Map.of("end_session_endpoint", endSessionEndpoint));
        }
        return builder.build();
    }

    private TestingAuthenticationToken authenticatedUser() {
        OidcIdToken idToken = new OidcIdToken(
                "id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, "user-123")
        );
        OidcUser oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(oidcUser, null);
        authentication.setAuthenticated(true);
        return authentication;
    }
}
