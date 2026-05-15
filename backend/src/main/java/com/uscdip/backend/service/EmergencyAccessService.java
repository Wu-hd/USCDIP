package com.uscdip.backend.service;

import com.uscdip.backend.dto.EmergencyAccountActivateRequest;
import com.uscdip.backend.dto.EmergencyAccountResponse;
import com.uscdip.backend.dto.EmergencyAccountRevokeRequest;
import com.uscdip.backend.dto.EmergencyAuditPageResponse;
import com.uscdip.backend.dto.EmergencyAuditRecordResponse;
import com.uscdip.backend.entity.EmergencyAccountEntity;
import com.uscdip.backend.entity.SecurityAuditEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.repository.EmergencyAccountRepository;
import com.uscdip.backend.repository.SecurityAuditRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmergencyAccessService {

    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    private final EmergencyAccountRepository emergencyAccountRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final LocalTokenService localTokenService;
    private final SecurityAuditService securityAuditService;
    private final TokenRevocationService tokenRevocationService;
    private final PasswordEncoder passwordEncoder;

    public EmergencyAccessService(
            EmergencyAccountRepository emergencyAccountRepository,
            SecurityAuditRepository securityAuditRepository,
            LocalTokenService localTokenService,
            SecurityAuditService securityAuditService,
            TokenRevocationService tokenRevocationService,
            PasswordEncoder passwordEncoder
    ) {
        this.emergencyAccountRepository = emergencyAccountRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.localTokenService = localTokenService;
        this.securityAuditService = securityAuditService;
        this.tokenRevocationService = tokenRevocationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public com.uscdip.backend.dto.TokenPairResponse login(String username, String password, String clientIp, String userAgent) {
        Optional<EmergencyAccountEntity> accountOptional = emergencyAccountRepository.findByUsernameIgnoreCase(username);
        if (accountOptional.isEmpty()) {
            securityAuditService.log(
                    SecurityAuditService.EVENT_BREAK_GLASS_LOGIN_FAILED,
                    null,
                    null,
                    null,
                    AuthMode.BREAK_GLASS,
                    null,
                    SecurityAuditService.OUTCOME_DENY,
                    "Emergency login failed: account not found",
                    clientIp,
                    userAgent
            );
            throw new AuthFlowException(ErrorCode.EMERGENCY_LOGIN_FAILED, HttpStatus.UNAUTHORIZED, ErrorCode.EMERGENCY_LOGIN_FAILED.defaultMessage());
        }

        EmergencyAccountEntity account = accountOptional.get();
        normalizeExpiration(account);
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            securityAuditService.log(
                    SecurityAuditService.EVENT_BREAK_GLASS_LOGIN_FAILED,
                    account.getLinkedUserId(),
                    null,
                    null,
                    AuthMode.BREAK_GLASS,
                    account.getAccountId(),
                    SecurityAuditService.OUTCOME_DENY,
                    "Emergency login failed: invalid credentials",
                    clientIp,
                    userAgent
            );
            throw new AuthFlowException(ErrorCode.EMERGENCY_LOGIN_FAILED, HttpStatus.UNAUTHORIZED, ErrorCode.EMERGENCY_LOGIN_FAILED.defaultMessage());
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(account.getStatus())) {
            securityAuditService.log(
                    SecurityAuditService.EVENT_BREAK_GLASS_LOGIN_FAILED,
                    account.getLinkedUserId(),
                    null,
                    null,
                    AuthMode.BREAK_GLASS,
                    account.getAccountId(),
                    SecurityAuditService.OUTCOME_DENY,
                    "Emergency login failed: account status is " + account.getStatus(),
                    clientIp,
                    userAgent
            );
            throw new AuthFlowException(
                    ErrorCode.EMERGENCY_ACCOUNT_NOT_AVAILABLE,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.EMERGENCY_ACCOUNT_NOT_AVAILABLE.defaultMessage()
            );
        }

        account.setLastLoginAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        emergencyAccountRepository.save(account);

        com.uscdip.backend.dto.TokenPairResponse tokenPair = localTokenService.issueForEmergencyAccount(account, clientIp, userAgent);
        securityAuditService.log(
                SecurityAuditService.EVENT_BREAK_GLASS_LOGIN_SUCCESS,
                account.getLinkedUserId(),
                null,
                null,
                AuthMode.BREAK_GLASS,
                account.getAccountId(),
                SecurityAuditService.OUTCOME_SUCCESS,
                "Emergency account login succeeded",
                clientIp,
                userAgent
        );
        return tokenPair;
    }

    @Transactional
    public EmergencyAccountResponse activate(
            String accountId,
            EmergencyAccountActivateRequest request,
            String actorUserId,
            String clientIp,
            String userAgent
    ) {
        EmergencyAccountEntity account = loadAccount(accountId);
        LocalDateTime now = LocalDateTime.now();
        tokenRevocationService.revokeEmergencyAccountTokens(accountId, "ACCOUNT_REACTIVATED");

        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setStatus(STATUS_ACTIVE);
        account.setExpiresAt(request.expiresAt());
        account.setActivatedAt(now);
        account.setActivatedBy(actorUserId);
        account.setRevokedAt(null);
        account.setRevokeReason(null);
        account.setUpdatedAt(now);
        emergencyAccountRepository.save(account);

        securityAuditService.log(
                SecurityAuditService.EVENT_BREAK_GLASS_ACCOUNT_ACTIVATED,
                account.getLinkedUserId(),
                null,
                null,
                AuthMode.BREAK_GLASS,
                account.getAccountId(),
                SecurityAuditService.OUTCOME_SUCCESS,
                "Emergency account activated by %s: %s".formatted(actorUserId, request.reason()),
                clientIp,
                userAgent
        );
        return toResponse(account);
    }

    @Transactional
    public EmergencyAccountResponse revoke(
            String accountId,
            EmergencyAccountRevokeRequest request,
            String actorUserId,
            String clientIp,
            String userAgent
    ) {
        EmergencyAccountEntity account = loadAccount(accountId);
        LocalDateTime now = LocalDateTime.now();

        account.setStatus(STATUS_REVOKED);
        account.setRevokedAt(now);
        account.setRevokeReason(request.reason());
        account.setUpdatedAt(now);
        emergencyAccountRepository.save(account);
        tokenRevocationService.revokeEmergencyAccountTokens(accountId, "ACCOUNT_REVOKED");

        securityAuditService.log(
                SecurityAuditService.EVENT_BREAK_GLASS_ACCOUNT_REVOKED,
                account.getLinkedUserId(),
                null,
                null,
                AuthMode.BREAK_GLASS,
                account.getAccountId(),
                SecurityAuditService.OUTCOME_SUCCESS,
                "Emergency account revoked by %s: %s".formatted(actorUserId, request.reason()),
                clientIp,
                userAgent
        );
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public EmergencyAuditPageResponse queryAudit(
            LocalDateTime from,
            LocalDateTime to,
            String username,
            String outcome,
            int page,
            int pageSize
    ) {
        List<SecurityAuditEntity> events = securityAuditRepository.findByEventTypeStartingWith(
                "BREAK_GLASS_",
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        if (from != null) {
            events = events.stream().filter(event -> !event.getCreatedAt().isBefore(from)).toList();
        }
        if (to != null) {
            events = events.stream().filter(event -> !event.getCreatedAt().isAfter(to)).toList();
        }
        if (outcome != null && !outcome.isBlank()) {
            events = events.stream()
                    .filter(event -> outcome.equalsIgnoreCase(event.getOutcome()))
                    .toList();
        }

        Map<String, EmergencyAccountEntity> accountMap = emergencyAccountRepository.findAll().stream()
                .collect(Collectors.toMap(EmergencyAccountEntity::getAccountId, Function.identity()));

        if (username != null && !username.isBlank()) {
            String normalizedUsername = username.trim();
            events = events.stream()
                    .filter(event -> {
                        EmergencyAccountEntity account = accountMap.get(event.getEmergencyAccountId());
                        return account != null && normalizedUsername.equalsIgnoreCase(account.getUsername());
                    })
                    .toList();
        }

        long total = events.size();
        int safePage = Math.max(page, 0);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min(safePage * safePageSize, events.size());
        int toIndex = Math.min(fromIndex + safePageSize, events.size());
        List<EmergencyAuditRecordResponse> records = events.subList(fromIndex, toIndex).stream()
                .map(event -> {
                    EmergencyAccountEntity account = accountMap.get(event.getEmergencyAccountId());
                    return new EmergencyAuditRecordResponse(
                            event.getEventType(),
                            account == null ? null : account.getUsername(),
                            event.getUserId(),
                            event.getSessionId(),
                            event.getOutcome(),
                            event.getDetail(),
                            event.getCreatedAt(),
                            event.getTraceId()
                    );
                })
                .toList();

        return new EmergencyAuditPageResponse(records, total, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Optional<EmergencyAccountEntity> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return emergencyAccountRepository.findByUsernameIgnoreCase(username.trim());
    }

    private EmergencyAccountEntity loadAccount(String accountId) {
        return emergencyAccountRepository.findById(accountId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Emergency account not found: " + accountId));
    }

    private void normalizeExpiration(EmergencyAccountEntity account) {
        if (account.getExpiresAt() != null && account.getExpiresAt().isBefore(LocalDateTime.now())
                && !STATUS_EXPIRED.equalsIgnoreCase(account.getStatus())) {
            account.setStatus(STATUS_EXPIRED);
            account.setUpdatedAt(LocalDateTime.now());
            emergencyAccountRepository.save(account);
            tokenRevocationService.revokeEmergencyAccountTokens(account.getAccountId(), "ACCOUNT_EXPIRED");
        }
    }

    private EmergencyAccountResponse toResponse(EmergencyAccountEntity account) {
        return new EmergencyAccountResponse(
                account.getAccountId(),
                account.getUsername(),
                account.getLinkedUserId(),
                account.getStatus(),
                account.getExpiresAt(),
                account.getActivatedAt(),
                account.getActivatedBy()
        );
    }
}
