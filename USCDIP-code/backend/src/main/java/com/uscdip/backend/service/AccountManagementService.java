package com.uscdip.backend.service;

import com.uscdip.backend.dto.AccountCreateRequest;
import com.uscdip.backend.dto.AccountOptionsResponse;
import com.uscdip.backend.dto.AccountPageResponse;
import com.uscdip.backend.dto.AccountPasswordResetRequest;
import com.uscdip.backend.dto.AccountResponse;
import com.uscdip.backend.dto.AccountRoleOptionResponse;
import com.uscdip.backend.dto.AccountUpdateRequest;
import com.uscdip.backend.entity.EmergencyAccountEntity;
import com.uscdip.backend.entity.RoleEntity;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.entity.UserDataScopeEntity;
import com.uscdip.backend.entity.UserRoleEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.repository.EmergencyAccountRepository;
import com.uscdip.backend.repository.RoleRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import com.uscdip.backend.repository.UserDataScopeRepository;
import com.uscdip.backend.repository.UserRoleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountManagementService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final Set<String> ALLOWED_SCOPE_TYPES = Set.of("REGION", "ASSIGNEE", "DATA_VIEW", "REGION_AGGREGATE");
    private static final LocalDateTime LOCAL_ACCOUNT_EXPIRY = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserDataScopeRepository userDataScopeRepository;
    private final RoleRepository roleRepository;
    private final EmergencyAccountRepository emergencyAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;
    private final SecurityAuditService securityAuditService;

    public AccountManagementService(
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            UserDataScopeRepository userDataScopeRepository,
            RoleRepository roleRepository,
            EmergencyAccountRepository emergencyAccountRepository,
            PasswordEncoder passwordEncoder,
            TokenRevocationService tokenRevocationService,
            SecurityAuditService securityAuditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.userDataScopeRepository = userDataScopeRepository;
        this.roleRepository = roleRepository;
        this.emergencyAccountRepository = emergencyAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRevocationService = tokenRevocationService;
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public AccountPageResponse list(String keyword, String status, int page, int pageSize) {
        List<AccountResponse> all = userAccountRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .map(this::toResponse)
                .toList();
        String normalizedKeyword = normalizeSearch(keyword);
        String normalizedStatus = normalizeOptional(status);
        List<AccountResponse> filtered = all.stream()
                .filter(account -> normalizedKeyword == null
                        || contains(account.userId(), normalizedKeyword)
                        || contains(account.username(), normalizedKeyword)
                        || contains(account.displayName(), normalizedKeyword))
                .filter(account -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(account.status()))
                .toList();

        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int fromIndex = Math.min((safePage - 1) * safePageSize, filtered.size());
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil(filtered.size() / (double) safePageSize);
        long activeCount = all.stream().filter(account -> STATUS_ACTIVE.equalsIgnoreCase(account.status())).count();
        long disabledCount = all.stream().filter(account -> STATUS_DISABLED.equalsIgnoreCase(account.status())).count();
        long platformAdminCount = all.stream().filter(account -> account.roleCodes().contains(ROLE_PLATFORM_ADMIN)).count();

        return new AccountPageResponse(
                filtered.subList(fromIndex, toIndex),
                filtered.size(),
                safePage,
                safePageSize,
                totalPages,
                safePage < totalPages,
                activeCount,
                disabledCount,
                platformAdminCount
        );
    }

    @Transactional(readOnly = true)
    public AccountOptionsResponse options() {
        List<AccountRoleOptionResponse> roles = roleRepository.findAll().stream()
                .sorted(Comparator.comparing(RoleEntity::getRoleCode))
                .map(role -> new AccountRoleOptionResponse(
                        role.getRoleCode(),
                        role.getRoleName(),
                        role.getDescription(),
                        Boolean.TRUE.equals(role.getReadOnly())
                ))
                .toList();
        return new AccountOptionsResponse(
                roles,
                List.of(STATUS_ACTIVE, STATUS_DISABLED),
                ALLOWED_SCOPE_TYPES.stream().sorted().toList()
        );
    }

    @Transactional
    public AccountResponse create(
            AccountCreateRequest request,
            String actorUserId,
            String clientIp,
            String userAgent
    ) {
        String username = request.username().trim();
        ensureUsernameAvailable(username);
        List<String> roleCodes = validateRoles(request.roleCodes());
        Map<String, List<String>> dataScopes = normalizeScopes(request.dataScopes());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String userId = "U-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);

        UserAccountEntity user = new UserAccountEntity(
                userId,
                username,
                request.displayName().trim(),
                request.primaryRegionId().trim().toUpperCase(Locale.ROOT),
                STATUS_ACTIVE,
                null,
                null,
                now,
                now
        );
        userAccountRepository.save(user);
        replaceRoles(userId, roleCodes);
        replaceScopes(userId, dataScopes);

        EmergencyAccountEntity credential = new EmergencyAccountEntity(
                "EA-LOCAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT),
                username,
                userId,
                passwordEncoder.encode(request.password()),
                STATUS_ACTIVE,
                LOCAL_ACCOUNT_EXPIRY,
                now,
                actorUserId,
                null,
                null,
                null,
                now,
                now
        );
        emergencyAccountRepository.save(credential);
        securityAuditService.log(
                SecurityAuditService.EVENT_ACCOUNT_CREATED,
                userId,
                null,
                null,
                SecurityAuditService.OUTCOME_SUCCESS,
                "Account created by " + actorUserId,
                clientIp,
                userAgent
        );
        return toResponse(user);
    }

    @Transactional
    public AccountResponse update(
            String userId,
            AccountUpdateRequest request,
            String actorUserId,
            String clientIp,
            String userAgent
    ) {
        UserAccountEntity user = loadUser(userId);
        List<String> roleCodes = validateRoles(request.roleCodes());
        Map<String, List<String>> dataScopes = normalizeScopes(request.dataScopes());
        String requestedStatus = request.status().trim().toUpperCase(Locale.ROOT);
        List<String> previousRoles = currentRoles(userId);
        Map<String, List<String>> previousScopes = currentScopes(userId);
        String previousStatus = user.getStatus();

        if (actorUserId.equalsIgnoreCase(userId) && STATUS_DISABLED.equals(requestedStatus)) {
            throw invalidOperation("You cannot disable your own account");
        }
        if (actorUserId.equalsIgnoreCase(userId) && !roleCodes.contains(ROLE_PLATFORM_ADMIN)) {
            throw invalidOperation("You cannot remove your own PLATFORM_ADMIN role");
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        user.setDisplayName(request.displayName().trim());
        user.setPrimaryRegionId(request.primaryRegionId().trim().toUpperCase(Locale.ROOT));
        user.setStatus(requestedStatus);
        user.setDisabledAt(STATUS_DISABLED.equals(requestedStatus) ? now : null);
        user.setUpdatedAt(now);
        userAccountRepository.save(user);
        replaceRoles(userId, roleCodes);
        replaceScopes(userId, dataScopes);

        boolean securityChanged = !new LinkedHashSet<>(previousRoles).equals(new LinkedHashSet<>(roleCodes))
                || !previousScopes.equals(dataScopes)
                || !previousStatus.equalsIgnoreCase(requestedStatus);

        if (STATUS_DISABLED.equals(requestedStatus)) {
            revokeLocalCredential(user, actorUserId, now);
            tokenRevocationService.disableUser(userId, actorUserId, clientIp, userAgent);
        } else {
            if (STATUS_DISABLED.equalsIgnoreCase(previousStatus)) {
                activateLocalCredential(user, actorUserId, now);
                securityAuditService.log(
                        SecurityAuditService.EVENT_ACCOUNT_ENABLED,
                        userId,
                        null,
                        null,
                        SecurityAuditService.OUTCOME_SUCCESS,
                        "Account enabled by " + actorUserId,
                        clientIp,
                        userAgent
                );
            }
            if (securityChanged) {
                tokenRevocationService.revokeUserPermissions(userId, actorUserId, clientIp, userAgent);
            }
        }

        securityAuditService.log(
                SecurityAuditService.EVENT_ACCOUNT_UPDATED,
                userId,
                null,
                null,
                SecurityAuditService.OUTCOME_SUCCESS,
                "Account profile updated by " + actorUserId,
                clientIp,
                userAgent
        );
        return toResponse(loadUser(userId));
    }

    @Transactional
    public AccountResponse resetPassword(
            String userId,
            AccountPasswordResetRequest request,
            String actorUserId,
            String clientIp,
            String userAgent
    ) {
        UserAccountEntity user = loadUser(userId);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        EmergencyAccountEntity credential = findLocalCredential(user).orElseGet(() -> new EmergencyAccountEntity(
                "EA-LOCAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT),
                user.getUsername(),
                userId,
                "",
                STATUS_DISABLED.equalsIgnoreCase(user.getStatus()) ? "REVOKED" : STATUS_ACTIVE,
                LOCAL_ACCOUNT_EXPIRY,
                now,
                actorUserId,
                null,
                null,
                null,
                now,
                now
        ));
        credential.setPasswordHash(passwordEncoder.encode(request.password()));
        credential.setUpdatedAt(now);
        if (STATUS_ACTIVE.equalsIgnoreCase(user.getStatus())) {
            credential.setStatus(STATUS_ACTIVE);
            credential.setExpiresAt(LOCAL_ACCOUNT_EXPIRY);
            credential.setActivatedAt(now);
            credential.setActivatedBy(actorUserId);
            credential.setRevokedAt(null);
            credential.setRevokeReason(null);
        }
        emergencyAccountRepository.save(credential);
        tokenRevocationService.revokeEmergencyAccountTokens(credential.getAccountId(), "PASSWORD_RESET");
        securityAuditService.log(
                SecurityAuditService.EVENT_ACCOUNT_PASSWORD_RESET,
                userId,
                null,
                null,
                SecurityAuditService.OUTCOME_SUCCESS,
                "Password reset by " + actorUserId,
                clientIp,
                userAgent
        );
        return toResponse(user);
    }

    private AccountResponse toResponse(UserAccountEntity user) {
        List<String> roles = currentRoles(user.getUserId());
        Map<String, List<String>> scopes = currentScopes(user.getUserId());
        Optional<EmergencyAccountEntity> credential = findLocalCredential(user);
        boolean localLoginEnabled = credential
                .map(account -> STATUS_ACTIVE.equalsIgnoreCase(account.getStatus())
                        && (account.getExpiresAt() == null || account.getExpiresAt().isAfter(LocalDateTime.now())))
                .orElse(false);
        return new AccountResponse(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPrimaryRegionId(),
                user.getStatus(),
                roles,
                scopes,
                localLoginEnabled,
                credential.map(EmergencyAccountEntity::getLastLoginAt).orElse(null),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private void ensureUsernameAvailable(String username) {
        if (userAccountRepository.existsByUsernameIgnoreCase(username)
                || emergencyAccountRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new AuthFlowException(ErrorCode.ACCOUNT_CONFLICT, HttpStatus.CONFLICT, "Username already exists: " + username);
        }
    }

    private List<String> validateRoles(Collection<String> requestedRoles) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String roleCode : requestedRoles) {
            if (roleCode != null && !roleCode.isBlank()) {
                normalized.add(roleCode.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        Set<String> existing = new LinkedHashSet<>();
        roleRepository.findAllById(normalized).forEach(role -> existing.add(role.getRoleCode()));
        if (!existing.containsAll(normalized)) {
            LinkedHashSet<String> unknown = new LinkedHashSet<>(normalized);
            unknown.removeAll(existing);
            throw new IllegalArgumentException("Unknown role codes: " + String.join(", ", unknown));
        }
        return List.copyOf(normalized);
    }

    private Map<String, List<String>> normalizeScopes(Map<String, List<String>> requestedScopes) {
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        if (requestedScopes == null) return normalized;
        requestedScopes.forEach((scopeType, values) -> {
            String normalizedType = scopeType == null ? "" : scopeType.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_SCOPE_TYPES.contains(normalizedType)) {
                throw new IllegalArgumentException("Unsupported data scope type: " + scopeType);
            }
            LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
            if (values != null) {
                values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.trim().toUpperCase(Locale.ROOT))
                        .forEach(value -> {
                            if (value.length() > 128) {
                                throw new IllegalArgumentException("Data scope value exceeds 128 characters");
                            }
                            normalizedValues.add(value);
                        });
            }
            if (!normalizedValues.isEmpty()) normalized.put(normalizedType, List.copyOf(normalizedValues));
        });
        return normalized;
    }

    private void replaceRoles(String userId, List<String> roleCodes) {
        userRoleRepository.deleteByUserId(userId);
        userRoleRepository.saveAll(roleCodes.stream().map(roleCode -> new UserRoleEntity(null, userId, roleCode)).toList());
    }

    private void replaceScopes(String userId, Map<String, List<String>> scopes) {
        userDataScopeRepository.deleteByUserId(userId);
        List<UserDataScopeEntity> entities = new ArrayList<>();
        scopes.forEach((scopeType, values) -> values.forEach(value -> entities.add(new UserDataScopeEntity(null, userId, scopeType, value))));
        userDataScopeRepository.saveAll(entities);
    }

    private List<String> currentRoles(String userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRoleEntity::getRoleCode)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    private Map<String, List<String>> currentScopes(String userId) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        userDataScopeRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(UserDataScopeEntity::getScopeType).thenComparing(UserDataScopeEntity::getScopeValue))
                .forEach(scope -> result.computeIfAbsent(scope.getScopeType().toUpperCase(Locale.ROOT), ignored -> new ArrayList<>())
                        .add(scope.getScopeValue().toUpperCase(Locale.ROOT)));
        result.replaceAll((ignored, values) -> List.copyOf(values));
        return result;
    }

    private Optional<EmergencyAccountEntity> findLocalCredential(UserAccountEntity user) {
        return emergencyAccountRepository.findByLinkedUserIdOrderByCreatedAtAsc(user.getUserId()).stream()
                .filter(account -> account.getUsername().equalsIgnoreCase(user.getUsername()))
                .findFirst();
    }

    private void revokeLocalCredential(UserAccountEntity user, String actorUserId, LocalDateTime now) {
        findLocalCredential(user).ifPresent(credential -> {
            credential.setStatus("REVOKED");
            credential.setRevokedAt(now);
            credential.setRevokeReason("User account disabled by " + actorUserId);
            credential.setUpdatedAt(now);
            emergencyAccountRepository.save(credential);
            tokenRevocationService.revokeEmergencyAccountTokens(credential.getAccountId(), "USER_ACCOUNT_DISABLED");
        });
    }

    private void activateLocalCredential(UserAccountEntity user, String actorUserId, LocalDateTime now) {
        findLocalCredential(user).ifPresent(credential -> {
            credential.setStatus(STATUS_ACTIVE);
            credential.setExpiresAt(LOCAL_ACCOUNT_EXPIRY);
            credential.setActivatedAt(now);
            credential.setActivatedBy(actorUserId);
            credential.setRevokedAt(null);
            credential.setRevokeReason(null);
            credential.setUpdatedAt(now);
            emergencyAccountRepository.save(credential);
        });
    }

    private UserAccountEntity loadUser(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    private AuthFlowException invalidOperation(String message) {
        return new AuthFlowException(ErrorCode.ACCOUNT_INVALID_OPERATION, HttpStatus.CONFLICT, message);
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
