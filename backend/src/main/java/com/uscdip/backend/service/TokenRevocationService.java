package com.uscdip.backend.service;

import com.uscdip.backend.dto.TokenRevocationResponse;
import com.uscdip.backend.entity.AuthRefreshTokenEntity;
import com.uscdip.backend.entity.AuthSessionEntity;
import com.uscdip.backend.entity.EmergencyAccountEntity;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.repository.AuthRefreshTokenRepository;
import com.uscdip.backend.repository.AuthSessionRepository;
import com.uscdip.backend.repository.EmergencyAccountRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TokenRevocationService {

    public static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    public static final String SESSION_STATUS_REVOKED = "REVOKED";
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final AuthSessionRepository authSessionRepository;
    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final EmergencyAccountRepository emergencyAccountRepository;
    private final UserAccountRepository userAccountRepository;
    private final SecurityAuditService securityAuditService;

    public TokenRevocationService(
            AuthSessionRepository authSessionRepository,
            AuthRefreshTokenRepository authRefreshTokenRepository,
            EmergencyAccountRepository emergencyAccountRepository,
            UserAccountRepository userAccountRepository,
            SecurityAuditService securityAuditService
    ) {
        this.authSessionRepository = authSessionRepository;
        this.authRefreshTokenRepository = authRefreshTokenRepository;
        this.emergencyAccountRepository = emergencyAccountRepository;
        this.userAccountRepository = userAccountRepository;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public void ensureSessionRecorded(String userId, String sessionId, String clientIp, String userAgent) {
        ensureSessionRecorded(userId, sessionId, AuthMode.STANDARD, null, clientIp, userAgent);
    }

    @Transactional
    public void ensureSessionRecorded(
            String userId,
            String sessionId,
            String authMode,
            String emergencyAccountId,
            String clientIp,
            String userAgent
    ) {
        LocalDateTime now = LocalDateTime.now();
        AuthSessionEntity session = authSessionRepository.findById(sessionId)
                .orElseGet(() -> new AuthSessionEntity(
                        sessionId,
                        userId,
                        authMode,
                        emergencyAccountId,
                        SESSION_STATUS_ACTIVE,
                        null,
                        null,
                        clientIp,
                        truncate(userAgent, 512),
                        now,
                        now
                ));
        session.setUserId(userId);
        session.setAuthMode(authMode);
        session.setEmergencyAccountId(emergencyAccountId);
        session.setStatus(SESSION_STATUS_ACTIVE);
        session.setClientIp(clientIp);
        session.setUserAgent(truncate(userAgent, 512));
        session.setUpdatedAt(now);
        authSessionRepository.save(session);
    }

    @Transactional
    public void validateAccessToken(
            LocalAccessTokenService.AccessTokenPrincipal principal,
            String clientIp,
            String userAgent
    ) {
        UserAccountEntity user = userAccountRepository.findById(principal.userId())
                .orElseThrow(() -> accessRejected(principal.userId(), principal.tokenId(), principal.sessionId(), "User not found", clientIp, userAgent));
        ensureUserAllowsToken(user, principal.issuedAt(), principal.tokenId(), principal.sessionId(), clientIp, userAgent);

        AuthSessionEntity session = authSessionRepository.findById(principal.sessionId())
                .orElseThrow(() -> accessRejected(principal.userId(), principal.tokenId(), principal.sessionId(), "Session not found", clientIp, userAgent));
        if (!SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
            throw accessRejected(principal.userId(), principal.tokenId(), principal.sessionId(), "Session is revoked", clientIp, userAgent);
        }
        ensureEmergencyAccountAllowsToken(
                principal.authMode(),
                principal.emergencyAccountId(),
                principal.userId(),
                principal.tokenId(),
                principal.sessionId(),
                clientIp,
                userAgent
        );
    }

    @Transactional
    public void validateRefreshToken(AuthRefreshTokenEntity refreshToken, UserAccountEntity user, String clientIp, String userAgent) {
        ensureUserAllowsRefreshToken(user, refreshToken, clientIp, userAgent);
        AuthSessionEntity session = authSessionRepository.findById(refreshToken.getSessionId())
                .orElseThrow(() -> refreshRejected(refreshToken, "Session not found", clientIp, userAgent));
        if (!SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
            throw refreshRejected(refreshToken, "Session is revoked", clientIp, userAgent);
        }
        ensureEmergencyAccountAllowsRefreshToken(refreshToken, clientIp, userAgent);
    }

    @Transactional
    public TokenRevocationResponse revokeCurrentSession(String userId, String sessionId, String clientIp, String userAgent) {
        int revokedRefreshTokenCount = revokeRefreshTokensBySession(sessionId, "LOGOUT");
        int revokedSessionCount = revokeSessionIfActive(sessionId, "LOGOUT");
        securityAuditService.log(
                SecurityAuditService.EVENT_LOGOUT_SUCCESS,
                userId,
                null,
                sessionId,
                resolveSessionAuthMode(sessionId),
                resolveSessionEmergencyAccountId(sessionId),
                SecurityAuditService.OUTCOME_SUCCESS,
                "Current session logout completed",
                clientIp,
                userAgent
        );
        return new TokenRevocationResponse(userId, "LOGOUT", null, revokedSessionCount, revokedRefreshTokenCount);
    }

    @Transactional
    public TokenRevocationResponse disableUser(String targetUserId, String actorUserId, String clientIp, String userAgent) {
        UserAccountEntity user = loadUser(targetUserId);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        user.setStatus("DISABLED");
        user.setDisabledAt(now);
        user.setTokenValidAfter(now);
        user.setUpdatedAt(now);
        userAccountRepository.save(user);

        int revokedSessionCount = revokeSessionsByUser(targetUserId, "ACCOUNT_DISABLED");
        int revokedRefreshTokenCount = revokeRefreshTokensByUser(targetUserId, "ACCOUNT_DISABLED");
        securityAuditService.log(
                SecurityAuditService.EVENT_USER_DISABLED,
                targetUserId,
                null,
                null,
                SecurityAuditService.OUTCOME_SUCCESS,
                "User disabled by " + actorUserId,
                clientIp,
                userAgent
        );
        return new TokenRevocationResponse(targetUserId, "DISABLE_USER", now, revokedSessionCount, revokedRefreshTokenCount);
    }

    @Transactional
    public TokenRevocationResponse revokeUserPermissions(String targetUserId, String actorUserId, String clientIp, String userAgent) {
        UserAccountEntity user = loadUser(targetUserId);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        user.setTokenValidAfter(now);
        user.setUpdatedAt(now);
        userAccountRepository.save(user);

        int revokedSessionCount = revokeSessionsByUser(targetUserId, "PERMISSION_CHANGED");
        int revokedRefreshTokenCount = revokeRefreshTokensByUser(targetUserId, "PERMISSION_CHANGED");
        securityAuditService.log(
                SecurityAuditService.EVENT_PERMISSION_REVOKED,
                targetUserId,
                null,
                null,
                SecurityAuditService.OUTCOME_SUCCESS,
                "Permission convergence requested by " + actorUserId,
                clientIp,
                userAgent
        );
        return new TokenRevocationResponse(targetUserId, "REVOKE_PERMISSIONS", now, revokedSessionCount, revokedRefreshTokenCount);
    }

    private UserAccountEntity loadUser(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    private void ensureUserAllowsToken(
            UserAccountEntity user,
            Instant tokenIssuedAt,
            String tokenId,
            String sessionId,
            String clientIp,
            String userAgent
    ) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw accessRejected(user.getUserId(), tokenId, sessionId, "User account is disabled", clientIp, userAgent);
        }
        if (user.getTokenValidAfter() != null
                && tokenIssuedAt.isBefore(user.getTokenValidAfter().atZone(SYSTEM_ZONE).toInstant())) {
            throw accessRejected(user.getUserId(), tokenId, sessionId, "Access token was issued before token_valid_after", clientIp, userAgent);
        }
    }

    private void ensureEmergencyAccountAllowsToken(
            String authMode,
            String emergencyAccountId,
            String userId,
            String tokenId,
            String sessionId,
            String clientIp,
            String userAgent
    ) {
        if (!AuthMode.BREAK_GLASS.equals(authMode)) {
            return;
        }
        EmergencyAccountEntity emergencyAccount = loadEmergencyAccount(emergencyAccountId, userId, tokenId, sessionId, clientIp, userAgent);
        if (!isEmergencyAccountActive(emergencyAccount)) {
            revokeEmergencyAccountTokens(emergencyAccount.getAccountId(), "ACCOUNT_NOT_AVAILABLE");
            throw accessRejected(userId, tokenId, sessionId, "Emergency account is not active", clientIp, userAgent, authMode, emergencyAccountId);
        }
        if (isEmergencyAccountExpired(emergencyAccount)) {
            expireEmergencyAccount(emergencyAccount);
            revokeEmergencyAccountTokens(emergencyAccount.getAccountId(), "ACCOUNT_EXPIRED");
            throw accessRejected(userId, tokenId, sessionId, "Emergency account is expired", clientIp, userAgent, authMode, emergencyAccountId);
        }
    }

    private void ensureUserAllowsRefreshToken(
            UserAccountEntity user,
            AuthRefreshTokenEntity refreshToken,
            String clientIp,
            String userAgent
    ) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw refreshRejected(refreshToken, "User account is disabled", clientIp, userAgent);
        }
        if (user.getTokenValidAfter() != null
                && refreshToken.getIssuedAt().isBefore(user.getTokenValidAfter())) {
            throw refreshRejected(refreshToken, "Refresh token was issued before token_valid_after", clientIp, userAgent);
        }
    }

    private void ensureEmergencyAccountAllowsRefreshToken(
            AuthRefreshTokenEntity refreshToken,
            String clientIp,
            String userAgent
    ) {
        if (!AuthMode.BREAK_GLASS.equals(refreshToken.getAuthMode())) {
            return;
        }
        EmergencyAccountEntity emergencyAccount = emergencyAccountRepository.findById(refreshToken.getEmergencyAccountId())
                .orElseThrow(() -> refreshRejected(refreshToken, "Emergency account not found", clientIp, userAgent));
        if (!isEmergencyAccountActive(emergencyAccount)) {
            revokeEmergencyAccountTokens(emergencyAccount.getAccountId(), "ACCOUNT_NOT_AVAILABLE");
            throw refreshRejected(refreshToken, "Emergency account is not active", clientIp, userAgent);
        }
        if (isEmergencyAccountExpired(emergencyAccount)) {
            expireEmergencyAccount(emergencyAccount);
            revokeEmergencyAccountTokens(emergencyAccount.getAccountId(), "ACCOUNT_EXPIRED");
            throw refreshRejected(refreshToken, "Emergency account is expired", clientIp, userAgent);
        }
    }

    private AuthFlowException accessRejected(
            String userId,
            String tokenId,
            String sessionId,
            String detail,
            String clientIp,
            String userAgent
    ) {
        return accessRejected(userId, tokenId, sessionId, detail, clientIp, userAgent, AuthMode.STANDARD, null);
    }

    private AuthFlowException accessRejected(
            String userId,
            String tokenId,
            String sessionId,
            String detail,
            String clientIp,
            String userAgent,
            String authMode,
            String emergencyAccountId
    ) {
        securityAuditService.log(
                AuthMode.BREAK_GLASS.equals(authMode)
                        ? SecurityAuditService.EVENT_BREAK_GLASS_ACCESS_REJECTED
                        : SecurityAuditService.EVENT_ACCESS_REJECTED,
                userId,
                tokenId,
                sessionId,
                authMode,
                emergencyAccountId,
                SecurityAuditService.OUTCOME_DENY,
                detail,
                clientIp,
                userAgent
        );
        return new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, detail);
    }

    private AuthFlowException refreshRejected(
            AuthRefreshTokenEntity refreshToken,
            String detail,
            String clientIp,
            String userAgent
    ) {
        securityAuditService.log(
                AuthMode.BREAK_GLASS.equals(refreshToken.getAuthMode())
                        ? SecurityAuditService.EVENT_BREAK_GLASS_ACCESS_REJECTED
                        : SecurityAuditService.EVENT_REFRESH_REVOKED,
                refreshToken.getUserId(),
                refreshToken.getTokenId(),
                refreshToken.getSessionId(),
                refreshToken.getAuthMode(),
                refreshToken.getEmergencyAccountId(),
                SecurityAuditService.OUTCOME_DENY,
                detail,
                clientIp,
                userAgent
        );
        return new AuthFlowException(
                ErrorCode.TOKEN_REFRESH_REVOKED,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.TOKEN_REFRESH_REVOKED.defaultMessage()
        );
    }

    private int revokeSessionIfActive(String sessionId, String reason) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        return authSessionRepository.findById(sessionId)
                .map(session -> revokeSession(session, reason))
                .orElse(0);
    }

    private int revokeSessionsByUser(String userId, String reason) {
        int revokedCount = 0;
        List<AuthSessionEntity> sessions = authSessionRepository.findByUserId(userId);
        for (AuthSessionEntity session : sessions) {
            revokedCount += revokeSession(session, reason);
        }
        return revokedCount;
    }

    private int revokeSession(AuthSessionEntity session, String reason) {
        if (!SESSION_STATUS_ACTIVE.equals(session.getStatus())) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(SESSION_STATUS_REVOKED);
        session.setRevokedAt(now);
        session.setRevokeReason(reason);
        session.setUpdatedAt(now);
        authSessionRepository.save(session);
        securityAuditService.log(
                SecurityAuditService.EVENT_SESSION_REVOKED,
                session.getUserId(),
                null,
                session.getSessionId(),
                session.getAuthMode(),
                session.getEmergencyAccountId(),
                SecurityAuditService.OUTCOME_SUCCESS,
                "Session revoked due to " + reason,
                session.getClientIp(),
                session.getUserAgent()
        );
        return 1;
    }

    private int revokeRefreshTokensBySession(String sessionId, String reason) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        return revokeRefreshTokens(authRefreshTokenRepository.findBySessionId(sessionId), reason);
    }

    private int revokeRefreshTokensByUser(String userId, String reason) {
        return revokeRefreshTokens(authRefreshTokenRepository.findByUserId(userId), reason);
    }

    private int revokeRefreshTokens(List<AuthRefreshTokenEntity> refreshTokens, String reason) {
        int revokedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (AuthRefreshTokenEntity refreshToken : refreshTokens) {
            if (LocalTokenService.STATUS_ACTIVE.equals(refreshToken.getStatus())
                    || LocalTokenService.STATUS_ROTATED.equals(refreshToken.getStatus())) {
                refreshToken.setStatus(LocalTokenService.STATUS_REVOKED);
                refreshToken.setUpdatedAt(now);
                authRefreshTokenRepository.save(refreshToken);
                revokedCount++;
            }
        }
        return revokedCount;
    }

    @Transactional
    public int revokeEmergencyAccountTokens(String emergencyAccountId, String reason) {
        int revokedSessions = revokeSessionsByEmergencyAccount(emergencyAccountId, reason);
        int revokedRefreshTokens = revokeRefreshTokensByEmergencyAccount(emergencyAccountId, reason);
        return revokedSessions + revokedRefreshTokens;
    }

    private int revokeSessionsByEmergencyAccount(String emergencyAccountId, String reason) {
        int revokedCount = 0;
        for (AuthSessionEntity session : authSessionRepository.findByEmergencyAccountId(emergencyAccountId)) {
            revokedCount += revokeSession(session, reason);
        }
        return revokedCount;
    }

    private int revokeRefreshTokensByEmergencyAccount(String emergencyAccountId, String reason) {
        return revokeRefreshTokens(authRefreshTokenRepository.findByEmergencyAccountId(emergencyAccountId), reason);
    }

    private EmergencyAccountEntity loadEmergencyAccount(
            String emergencyAccountId,
            String userId,
            String tokenId,
            String sessionId,
            String clientIp,
            String userAgent
    ) {
        if (emergencyAccountId == null || emergencyAccountId.isBlank()) {
            throw accessRejected(userId, tokenId, sessionId, "Emergency account is missing", clientIp, userAgent, AuthMode.BREAK_GLASS, null);
        }
        return emergencyAccountRepository.findById(emergencyAccountId)
                .orElseThrow(() -> accessRejected(
                        userId,
                        tokenId,
                        sessionId,
                        "Emergency account not found",
                        clientIp,
                        userAgent,
                        AuthMode.BREAK_GLASS,
                        emergencyAccountId
                ));
    }

    private boolean isEmergencyAccountActive(EmergencyAccountEntity emergencyAccount) {
        return "ACTIVE".equalsIgnoreCase(emergencyAccount.getStatus());
    }

    private boolean isEmergencyAccountExpired(EmergencyAccountEntity emergencyAccount) {
        return emergencyAccount.getExpiresAt() != null && emergencyAccount.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private void expireEmergencyAccount(EmergencyAccountEntity emergencyAccount) {
        if ("EXPIRED".equalsIgnoreCase(emergencyAccount.getStatus())) {
            return;
        }
        emergencyAccount.setStatus("EXPIRED");
        emergencyAccount.setUpdatedAt(LocalDateTime.now());
        emergencyAccountRepository.save(emergencyAccount);
    }

    private String resolveSessionAuthMode(String sessionId) {
        return authSessionRepository.findById(sessionId)
                .map(AuthSessionEntity::getAuthMode)
                .orElse(AuthMode.STANDARD);
    }

    private String resolveSessionEmergencyAccountId(String sessionId) {
        return authSessionRepository.findById(sessionId)
                .map(AuthSessionEntity::getEmergencyAccountId)
                .orElse(null);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
