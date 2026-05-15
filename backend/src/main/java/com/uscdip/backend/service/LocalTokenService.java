package com.uscdip.backend.service;

import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.AuthRefreshTokenEntity;
import com.uscdip.backend.entity.EmergencyAccountEntity;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.repository.AuthRefreshTokenRepository;
import com.uscdip.backend.repository.EmergencyAccountRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalTokenService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ROTATED = "ROTATED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_REPLAY_BLOCKED = "REPLAY_BLOCKED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BackendOidcProperties oidcProperties;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final EmergencyAccountRepository emergencyAccountRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthorizationService authorizationService;
    private final LocalAccessTokenService localAccessTokenService;
    private final SecurityAuditService securityAuditService;
    private final TokenRevocationService tokenRevocationService;

    public LocalTokenService(
            BackendOidcProperties oidcProperties,
            AuthRefreshTokenRepository authRefreshTokenRepository,
            EmergencyAccountRepository emergencyAccountRepository,
            UserAccountRepository userAccountRepository,
            AuthorizationService authorizationService,
            LocalAccessTokenService localAccessTokenService,
            SecurityAuditService securityAuditService,
            TokenRevocationService tokenRevocationService
    ) {
        this.oidcProperties = oidcProperties;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
        this.emergencyAccountRepository = emergencyAccountRepository;
        this.userAccountRepository = userAccountRepository;
        this.authorizationService = authorizationService;
        this.localAccessTokenService = localAccessTokenService;
        this.securityAuditService = securityAuditService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Transactional
    public TokenPairResponse issueForUser(String userId, String clientIp, String userAgent) {
        UserAccountEntity userAccount = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + userId));
        if (!"ACTIVE".equalsIgnoreCase(userAccount.getStatus())) {
            throw new AuthFlowException(ErrorCode.ACCOUNT_DISABLED, HttpStatus.UNAUTHORIZED, ErrorCode.ACCOUNT_DISABLED.defaultMessage());
        }
        return createTokenPair(
                userAccount,
                UUID.randomUUID().toString(),
                null,
                clientIp,
                userAgent,
                AuthMode.STANDARD,
                null
        );
    }

    @Transactional
    public TokenPairResponse issueForEmergencyAccount(EmergencyAccountEntity emergencyAccount, String clientIp, String userAgent) {
        UserAccountEntity userAccount = userAccountRepository.findById(emergencyAccount.getLinkedUserId())
                .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + emergencyAccount.getLinkedUserId()));
        if (!"ACTIVE".equalsIgnoreCase(userAccount.getStatus())) {
            throw new AuthFlowException(ErrorCode.ACCOUNT_DISABLED, HttpStatus.UNAUTHORIZED, ErrorCode.ACCOUNT_DISABLED.defaultMessage());
        }
        return createTokenPair(
                userAccount,
                UUID.randomUUID().toString(),
                null,
                clientIp,
                userAgent,
                AuthMode.BREAK_GLASS,
                emergencyAccount.getAccountId()
        );
    }

    @Transactional
    public TokenPairResponse refresh(String plainRefreshToken, String clientIp, String userAgent) {
        String tokenHash = hashRefreshToken(plainRefreshToken);
        AuthRefreshTokenEntity currentToken = authRefreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthFlowException(
                        ErrorCode.TOKEN_REFRESH_INVALID,
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.TOKEN_REFRESH_INVALID.defaultMessage()
                ));

        LocalDateTime now = LocalDateTime.now();
        if (isExpired(currentToken, now)) {
            currentToken.setStatus(STATUS_EXPIRED);
            currentToken.setUpdatedAt(now);
            authRefreshTokenRepository.save(currentToken);
            securityAuditService.log(
                    SecurityAuditService.EVENT_REFRESH_EXPIRED,
                    currentToken.getUserId(),
                    currentToken.getTokenId(),
                    currentToken.getSessionId(),
                    SecurityAuditService.OUTCOME_DENY,
                    "Expired refresh token reused",
                    clientIp,
                    userAgent
            );
            throw new AuthFlowException(
                    ErrorCode.TOKEN_REFRESH_EXPIRED,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.TOKEN_REFRESH_EXPIRED.defaultMessage()
            );
        }

        if (STATUS_REVOKED.equals(currentToken.getStatus())) {
            securityAuditService.log(
                    SecurityAuditService.EVENT_REFRESH_REVOKED,
                    currentToken.getUserId(),
                    currentToken.getTokenId(),
                    currentToken.getSessionId(),
                    SecurityAuditService.OUTCOME_DENY,
                    "Revoked refresh token reused",
                    clientIp,
                    userAgent
            );
            blockSessionChain(currentToken.getSessionId(), currentToken.getTokenId());
            throw new AuthFlowException(
                    ErrorCode.TOKEN_REFRESH_REVOKED,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.TOKEN_REFRESH_REVOKED.defaultMessage()
            );
        }

        if (!STATUS_ACTIVE.equals(currentToken.getStatus())) {
            securityAuditService.log(
                    SecurityAuditService.EVENT_REFRESH_REPLAY,
                    currentToken.getUserId(),
                    currentToken.getTokenId(),
                    currentToken.getSessionId(),
                    SecurityAuditService.OUTCOME_DENY,
                    "Refresh token replay detected with status " + currentToken.getStatus(),
                    clientIp,
                    userAgent
            );
            blockSessionChain(currentToken.getSessionId(), currentToken.getTokenId());
            throw new AuthFlowException(
                    ErrorCode.TOKEN_REFRESH_REPLAY_DETECTED,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.TOKEN_REFRESH_REPLAY_DETECTED.defaultMessage()
            );
        }

        UserAccountEntity userAccount = userAccountRepository.findById(currentToken.getUserId())
                .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + currentToken.getUserId()));
        tokenRevocationService.validateRefreshToken(currentToken, userAccount, clientIp, userAgent);

        currentToken.setStatus(STATUS_ROTATED);
        currentToken.setUpdatedAt(now);
        TokenPairResponse tokenPairResponse = createTokenPair(
                userAccount,
                currentToken.getSessionId(),
                currentToken,
                clientIp,
                userAgent,
                currentToken.getAuthMode(),
                currentToken.getEmergencyAccountId()
        );
        authRefreshTokenRepository.save(currentToken);
        securityAuditService.log(
                AuthMode.BREAK_GLASS.equals(currentToken.getAuthMode())
                        ? SecurityAuditService.EVENT_BREAK_GLASS_TOKEN_REFRESH
                        : SecurityAuditService.EVENT_REFRESH_SUCCESS,
                currentToken.getUserId(),
                currentToken.getTokenId(),
                currentToken.getSessionId(),
                currentToken.getAuthMode(),
                currentToken.getEmergencyAccountId(),
                SecurityAuditService.OUTCOME_SUCCESS,
                "Refresh token rotated successfully",
                clientIp,
                userAgent
        );
        return tokenPairResponse;
    }

    private TokenPairResponse createTokenPair(
            UserAccountEntity userAccount,
            String sessionId,
            AuthRefreshTokenEntity rotatedFrom,
            String clientIp,
            String userAgent,
            String authMode,
            String emergencyAccountId
    ) {
        LocalAccessTokenService.AccessTokenIssueResult accessToken = localAccessTokenService.issue(
                userAccount.getUserId(),
                userAccount.getUsername(),
                sessionId,
                authMode,
                emergencyAccountId,
                AuthMode.BREAK_GLASS.equals(authMode)
                        ? oidcProperties.getEmergencyAccessTokenTtlSeconds()
                        : oidcProperties.getAccessTokenTtlSeconds()
        );

        String plainRefreshToken = generateRefreshToken();
        String refreshTokenId = UUID.randomUUID().toString();
        Instant refreshTokenExpiresAt = Instant.now().plusSeconds(
                AuthMode.BREAK_GLASS.equals(authMode)
                        ? oidcProperties.getEmergencyRefreshTokenTtlSeconds()
                        : oidcProperties.getRefreshTokenTtlSeconds()
        );
        LocalDateTime now = LocalDateTime.now();
        tokenRevocationService.ensureSessionRecorded(
                userAccount.getUserId(),
                sessionId,
                authMode,
                emergencyAccountId,
                clientIp,
                userAgent
        );

        AuthRefreshTokenEntity refreshTokenEntity = new AuthRefreshTokenEntity(
                refreshTokenId,
                userAccount.getUserId(),
                hashRefreshToken(plainRefreshToken),
                sessionId,
                authMode,
                emergencyAccountId,
                now,
                LocalDateTime.ofInstant(refreshTokenExpiresAt, ZoneId.systemDefault()),
                rotatedFrom == null ? null : rotatedFrom.getTokenId(),
                null,
                STATUS_ACTIVE,
                clientIp,
                truncate(userAgent, 512),
                now,
                now
        );
        authRefreshTokenRepository.save(refreshTokenEntity);

        if (rotatedFrom != null) {
            rotatedFrom.setReplacedByTokenId(refreshTokenId);
        }

        Map<String, Object> snapshot = authorizationService.getUserSnapshot(userAccount.getUserId())
                .orElse(Map.of(
                        "userId", userAccount.getUserId(),
                        "username", userAccount.getUsername(),
                        "displayName", userAccount.getDisplayName()
                ));

        return new TokenPairResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                plainRefreshToken,
                refreshTokenExpiresAt,
                "Bearer",
                snapshot
        );
    }

    private void blockSessionChain(String sessionId, String reusedTokenId) {
        LocalDateTime now = LocalDateTime.now();
        List<AuthRefreshTokenEntity> sessionTokens = authRefreshTokenRepository.findBySessionId(sessionId);
        for (AuthRefreshTokenEntity token : sessionTokens) {
            if (token.getTokenId().equals(reusedTokenId)) {
                continue;
            }
            if (STATUS_ACTIVE.equals(token.getStatus()) || STATUS_ROTATED.equals(token.getStatus())) {
                token.setStatus(STATUS_REPLAY_BLOCKED);
                token.setUpdatedAt(now);
                authRefreshTokenRepository.save(token);
            }
        }
    }

    private boolean isExpired(AuthRefreshTokenEntity refreshTokenEntity, LocalDateTime now) {
        return refreshTokenEntity.getExpiresAt() != null && refreshTokenEntity.getExpiresAt().isBefore(now);
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashRefreshToken(String plainRefreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(oidcProperties.getRefreshTokenHashAlgorithm());
            byte[] hashed = digest.digest(plainRefreshToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : hashed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new AuthFlowException(ErrorCode.TOKEN_ISSUE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to hash refresh token");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
