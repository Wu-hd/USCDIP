package com.uscdip.backend.service;

import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.entity.UserDataScopeEntity;
import com.uscdip.backend.entity.UserRoleEntity;
import com.uscdip.backend.repository.RoleRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import com.uscdip.backend.repository.UserDataScopeRepository;
import com.uscdip.backend.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcUserSyncServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserDataScopeRepository userDataScopeRepository;

    @Mock
    private RoleRepository roleRepository;

    private OidcUserSyncService oidcUserSyncService;

    @BeforeEach
    void setUp() {
        BackendOidcProperties oidcProperties = new BackendOidcProperties();
        oidcProperties.setIssuerUri("https://issuer.example/realms/uscdip");
        oidcProperties.setDefaultRole("LEADER_READONLY");
        oidcProperties.setDefaultRegion("REGION-HZ");

        oidcUserSyncService = new OidcUserSyncService(
                userAccountRepository,
                userRoleRepository,
                userDataScopeRepository,
                roleRepository,
                oidcProperties
        );

        when(userAccountRepository.findById(anyString())).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRoleRepository.save(any(UserRoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userDataScopeRepository.save(any(UserDataScopeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void syncJwtClaimsUsesPreferredUsernameAndNormalizedRealmRoleWithStableUserId() {
        when(roleRepository.existsById(anyString())).thenAnswer(invocation ->
                Set.of("REGIONAL_DISPATCHER", "LEADER_READONLY").contains(invocation.getArgument(0, String.class)));

        Map<String, Object> claims = Map.of(
                "iss", "https://issuer.example/realms/uscdip",
                "sub", "user-123",
                "preferred_username", "dispatcher_a",
                "name", "Dispatcher A",
                "region_scopes", List.of(" REGION-HZ ", "REGION-HZ"),
                "realm_access", Map.of("roles", List.of("role_regional_dispatcher"))
        );

        String firstUserId = oidcUserSyncService.syncJwtClaims(claims);
        String secondUserId = oidcUserSyncService.syncJwtClaims(claims);

        assertEquals(firstUserId, secondUserId);
        assertTrue(firstUserId.startsWith("OIDC-"));

        ArgumentCaptor<UserAccountEntity> userCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository, atLeastOnce()).save(userCaptor.capture());
        UserAccountEntity savedUser = userCaptor.getValue();
        assertEquals(firstUserId, savedUser.getUserId());
        assertEquals("dispatcher_a", savedUser.getUsername());
        assertEquals("Dispatcher A", savedUser.getDisplayName());
        assertEquals("REGION-HZ", savedUser.getPrimaryRegionId());

        ArgumentCaptor<UserRoleEntity> roleCaptor = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoleRepository, atLeastOnce()).save(roleCaptor.capture());
        assertEquals("REGIONAL_DISPATCHER", roleCaptor.getValue().getRoleCode());

        ArgumentCaptor<UserDataScopeEntity> scopeCaptor = ArgumentCaptor.forClass(UserDataScopeEntity.class);
        verify(userDataScopeRepository, atLeastOnce()).save(scopeCaptor.capture());
        assertEquals("REGION", scopeCaptor.getValue().getScopeType());
        assertEquals("REGION-HZ", scopeCaptor.getValue().getScopeValue());
    }

    @Test
    void syncJwtClaimsFallsBackToEmailDefaultRoleAndDefaultRegion() {
        when(roleRepository.existsById(anyString())).thenAnswer(invocation ->
                "LEADER_READONLY".equals(invocation.getArgument(0, String.class)));

        Map<String, Object> claims = Map.of(
                "iss", "https://issuer.example/realms/uscdip",
                "sub", "user-456",
                "email", "leader@example.com"
        );

        String userId = oidcUserSyncService.syncJwtClaims(claims);

        assertTrue(userId.startsWith("OIDC-"));

        ArgumentCaptor<UserAccountEntity> userCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository, atLeastOnce()).save(userCaptor.capture());
        UserAccountEntity savedUser = userCaptor.getValue();
        assertEquals("leader@example.com", savedUser.getUsername());
        assertEquals("leader@example.com", savedUser.getDisplayName());
        assertEquals("REGION-HZ", savedUser.getPrimaryRegionId());

        ArgumentCaptor<UserRoleEntity> roleCaptor = ArgumentCaptor.forClass(UserRoleEntity.class);
        verify(userRoleRepository, atLeastOnce()).save(roleCaptor.capture());
        assertEquals("LEADER_READONLY", roleCaptor.getValue().getRoleCode());

        ArgumentCaptor<UserDataScopeEntity> scopeCaptor = ArgumentCaptor.forClass(UserDataScopeEntity.class);
        verify(userDataScopeRepository, atLeastOnce()).save(scopeCaptor.capture());
        assertEquals("REGION-HZ", scopeCaptor.getValue().getScopeValue());
    }
}
