package com.uscdip.backend.service;

import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.AuthRefreshTokenEntity;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.repository.AuthRefreshTokenRepository;
import com.uscdip.backend.repository.EmergencyAccountRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalTokenServiceTest {

    @Mock
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmergencyAccountRepository emergencyAccountRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private LocalAccessTokenService localAccessTokenService;

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    private LocalTokenService localTokenService;

    @BeforeEach
    void setUp() {
        BackendOidcProperties oidcProperties = new BackendOidcProperties();
        oidcProperties.setRefreshTokenHashAlgorithm("SHA-256");
        oidcProperties.setRefreshTokenTtlSeconds(1209600);

        localTokenService = new LocalTokenService(
                oidcProperties,
                authRefreshTokenRepository,
                emergencyAccountRepository,
                userAccountRepository,
                authorizationService,
                localAccessTokenService,
                securityAuditService,
                tokenRevocationService
        );
    }

    @Test
    void issueForUserStoresRefreshTokenHashInsteadOfPlaintext() {
        stubTokenIssuance();
        when(userAccountRepository.findById("U-DISPATCH-001")).thenReturn(Optional.of(
                new UserAccountEntity("U-DISPATCH-001", "hz_dispatcher", "杭州区域调度员", "REGION-HZ", "ACTIVE", null, null, LocalDateTime.now(), LocalDateTime.now())
        ));
        when(authorizationService.getUserSnapshot("U-DISPATCH-001")).thenReturn(Optional.of(Map.of("userId", "U-DISPATCH-001")));

        TokenPairResponse tokenPairResponse = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");

        ArgumentCaptor<AuthRefreshTokenEntity> captor = ArgumentCaptor.forClass(AuthRefreshTokenEntity.class);
        verify(authRefreshTokenRepository).save(captor.capture());

        assertNotEquals(tokenPairResponse.refreshToken(), captor.getValue().getTokenHash());
        assertEquals(localTokenService.hashRefreshToken(tokenPairResponse.refreshToken()), captor.getValue().getTokenHash());
        assertEquals(LocalTokenService.STATUS_ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void refreshRotatesTokenAndLinksOldToNew() {
        stubTokenIssuance();
        String oldRefreshToken = "sample-refresh-active-001";
        AuthRefreshTokenEntity currentToken = new AuthRefreshTokenEntity(
                "RT-001",
                "U-DISPATCH-001",
                localTokenService.hashRefreshToken(oldRefreshToken),
                "SESSION-001",
                AuthMode.STANDARD,
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7),
                null,
                null,
                LocalTokenService.STATUS_ACTIVE,
                "127.0.0.1",
                "JUnit",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(authRefreshTokenRepository.findByTokenHash(localTokenService.hashRefreshToken(oldRefreshToken)))
                .thenReturn(Optional.of(currentToken));
        when(userAccountRepository.findById("U-DISPATCH-001")).thenReturn(Optional.of(
                new UserAccountEntity("U-DISPATCH-001", "hz_dispatcher", "杭州区域调度员", "REGION-HZ", "ACTIVE", null, null, LocalDateTime.now(), LocalDateTime.now())
        ));
        when(authorizationService.getUserSnapshot("U-DISPATCH-001")).thenReturn(Optional.of(Map.of("userId", "U-DISPATCH-001")));

        TokenPairResponse tokenPairResponse = localTokenService.refresh(oldRefreshToken, "127.0.0.1", "JUnit");

        assertEquals("Bearer", tokenPairResponse.tokenType());
        assertEquals(LocalTokenService.STATUS_ROTATED, currentToken.getStatus());
        ArgumentCaptor<AuthRefreshTokenEntity> captor = ArgumentCaptor.forClass(AuthRefreshTokenEntity.class);
        verify(authRefreshTokenRepository, atLeastOnce()).save(captor.capture());
        AuthRefreshTokenEntity newestToken = captor.getAllValues().stream()
                .filter(token -> !token.getTokenId().equals("RT-001"))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals("RT-001", newestToken.getRotatedFromTokenId());
        assertEquals(newestToken.getTokenId(), currentToken.getReplacedByTokenId());
    }

    @Test
    void refreshReplayBlocksRemainingSessionChain() {
        String replayedToken = "sample-refresh-rotated-old-001";
        AuthRefreshTokenEntity reusedToken = new AuthRefreshTokenEntity(
                "RT-OLD-001",
                "U-OIDC-001",
                localTokenService.hashRefreshToken(replayedToken),
                "SESSION-ROTATE-001",
                AuthMode.STANDARD,
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7),
                null,
                "RT-NEW-001",
                LocalTokenService.STATUS_ROTATED,
                "127.0.0.1",
                "JUnit",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        AuthRefreshTokenEntity activeChild = new AuthRefreshTokenEntity(
                "RT-NEW-001",
                "U-OIDC-001",
                localTokenService.hashRefreshToken("sample-refresh-rotated-new-001"),
                "SESSION-ROTATE-001",
                AuthMode.STANDARD,
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7),
                "RT-OLD-001",
                null,
                LocalTokenService.STATUS_ACTIVE,
                "127.0.0.1",
                "JUnit",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(authRefreshTokenRepository.findByTokenHash(localTokenService.hashRefreshToken(replayedToken)))
                .thenReturn(Optional.of(reusedToken));
        when(authRefreshTokenRepository.findBySessionId("SESSION-ROTATE-001"))
                .thenReturn(List.of(reusedToken, activeChild));

        AuthFlowException exception = assertThrows(
                AuthFlowException.class,
                () -> localTokenService.refresh(replayedToken, "127.0.0.1", "JUnit")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        assertEquals(LocalTokenService.STATUS_REPLAY_BLOCKED, activeChild.getStatus());
        verify(securityAuditService).log(
                eq(SecurityAuditService.EVENT_REFRESH_REPLAY),
                eq("U-OIDC-001"),
                eq("RT-OLD-001"),
                eq("SESSION-ROTATE-001"),
                eq(SecurityAuditService.OUTCOME_DENY),
                any(),
                eq("127.0.0.1"),
                eq("JUnit")
        );
    }

    private void stubTokenIssuance() {
        when(authRefreshTokenRepository.save(any(AuthRefreshTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(localAccessTokenService.issue(any(), any(), any(), any(), any(), anyLong())).thenReturn(
                new LocalAccessTokenService.AccessTokenIssueResult(
                        "access-token",
                        Instant.parse("2099-01-01T00:00:00Z"),
                        "AT-001"
                )
        );
    }
}
