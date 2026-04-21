package com.uscdip.backend.service;

import com.uscdip.backend.config.BackendOidcProperties;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.entity.UserDataScopeEntity;
import com.uscdip.backend.entity.UserRoleEntity;
import com.uscdip.backend.repository.RoleRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import com.uscdip.backend.repository.UserDataScopeRepository;
import com.uscdip.backend.repository.UserRoleRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class OidcUserSyncService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserDataScopeRepository userDataScopeRepository;
    private final RoleRepository roleRepository;
    private final BackendOidcProperties oidcProperties;

    public OidcUserSyncService(
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            UserDataScopeRepository userDataScopeRepository,
            RoleRepository roleRepository,
            BackendOidcProperties oidcProperties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.userDataScopeRepository = userDataScopeRepository;
        this.roleRepository = roleRepository;
        this.oidcProperties = oidcProperties;
    }

    @Transactional
    public String syncOidcUser(OidcUser oidcUser) {
        if (oidcUser == null) {
            throw new IllegalArgumentException("OIDC user is required");
        }
        String issuer = oidcUser.getIssuer() == null ? oidcProperties.getIssuerUri() : oidcUser.getIssuer().toString();
        String subject = oidcUser.getSubject();
        return synchronize(oidcUser.getClaims(), issuer, subject);
    }

    @Transactional
    public String syncJwtClaims(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            throw new IllegalArgumentException("OIDC claims are required");
        }
        String issuer = asString(claims.get("iss"));
        String subject = asString(claims.get("sub"));
        if (isBlank(issuer)) {
            issuer = oidcProperties.getIssuerUri();
        }
        return synchronize(claims, issuer, subject);
    }

    private String synchronize(Map<String, Object> claims, String issuer, String subject) {
        if (isBlank(subject)) {
            throw new IllegalArgumentException("OIDC subject claim is missing");
        }

        String userId = buildUserId(issuer, subject);
        String username = firstNonBlank(
                asString(claims.get("preferred_username")),
                asString(claims.get("email")),
                asString(claims.get("name")),
                userId.toLowerCase()
        );
        String displayName = firstNonBlank(asString(claims.get("name")), username);

        List<String> regionScopes = extractRegionScopes(claims);
        String primaryRegion = regionScopes.isEmpty() ? oidcProperties.getDefaultRegion() : regionScopes.get(0);

        UserAccountEntity user = userAccountRepository.findById(userId).orElseGet(UserAccountEntity::new);
        LocalDateTime now = LocalDateTime.now();
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(now);
        }
        user.setUserId(userId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPrimaryRegionId(primaryRegion);
        user.setStatus("ACTIVE");
        user.setUpdatedAt(now);
        userAccountRepository.save(user);

        synchronizeRoles(userId, claims);
        synchronizeDataScopes(userId, regionScopes);
        return userId;
    }

    private void synchronizeRoles(String userId, Map<String, Object> claims) {
        Set<String> roleCodes = extractRoleCodes(claims);
        if (roleCodes.isEmpty() && roleRepository.existsById(oidcProperties.getDefaultRole())) {
            roleCodes.add(oidcProperties.getDefaultRole());
        }

        List<String> validRoleCodes = roleCodes.stream()
                .map(this::normalizeRole)
                .filter(roleRepository::existsById)
                .distinct()
                .toList();

        userRoleRepository.deleteByUserId(userId);
        for (String roleCode : validRoleCodes) {
            userRoleRepository.save(new UserRoleEntity(null, userId, roleCode));
        }
    }

    private void synchronizeDataScopes(String userId, List<String> regionScopes) {
        List<String> effectiveScopes = new ArrayList<>(regionScopes);
        if (effectiveScopes.isEmpty() && !isBlank(oidcProperties.getDefaultRegion())) {
            effectiveScopes.add(oidcProperties.getDefaultRegion());
        }

        userDataScopeRepository.deleteByUserId(userId);
        for (String scope : effectiveScopes) {
            userDataScopeRepository.save(new UserDataScopeEntity(null, userId, "REGION", scope));
        }
    }

    private Set<String> extractRoleCodes(Map<String, Object> claims) {
        Set<String> roleCodes = new LinkedHashSet<>();
        roleCodes.addAll(extractStringCollection(claims.get("roles")));

        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            roleCodes.addAll(extractStringCollection(realmMap.get("roles")));
        }

        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resources) {
            for (Object clientValue : resources.values()) {
                if (clientValue instanceof Map<?, ?> clientMap) {
                    roleCodes.addAll(extractStringCollection(clientMap.get("roles")));
                }
            }
        }

        return roleCodes.stream()
                .map(this::normalizeRole)
                .filter(role -> !isBlank(role))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private List<String> extractRegionScopes(Map<String, Object> claims) {
        List<String> regionScopes = new ArrayList<>(extractStringCollection(claims.get("region_scopes")));
        if (regionScopes.isEmpty()) {
            String region = asString(claims.get("region"));
            if (!isBlank(region)) {
                regionScopes.add(region.trim());
            }
        }
        return regionScopes.stream().filter(scope -> !isBlank(scope)).map(String::trim).distinct().toList();
    }

    private String buildUserId(String issuer, String subject) {
        String source = firstNonBlank(issuer, oidcProperties.getIssuerUri()) + "|" + subject;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : hash) {
                hex.append(String.format("%02x", item));
            }
            return "OIDC-" + hex.substring(0, 24).toUpperCase();
        } catch (Exception ex) {
            String normalized = subject.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            return "OIDC-" + normalized.substring(0, Math.min(24, normalized.length()));
        }
    }

    private String normalizeRole(String roleCode) {
        if (isBlank(roleCode)) {
            return "";
        }
        String normalized = roleCode.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return normalized;
    }

    private List<String> extractStringCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::asString)
                    .filter(item -> !isBlank(item))
                    .toList();
        }
        return List.of();
    }

    private String asString(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
