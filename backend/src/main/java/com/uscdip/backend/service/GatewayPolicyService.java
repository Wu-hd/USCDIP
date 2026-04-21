package com.uscdip.backend.service;

import com.uscdip.backend.entity.GatewayClientRuleEntity;
import com.uscdip.backend.entity.GatewayRiskAuditEntity;
import com.uscdip.backend.entity.GatewayRoutePolicyEntity;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.repository.GatewayClientRuleRepository;
import com.uscdip.backend.repository.GatewayRiskAuditRepository;
import com.uscdip.backend.repository.GatewayRoutePolicyRepository;
import com.uscdip.backend.support.TraceIdContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class GatewayPolicyService {

    public static final String RULE_ALLOWLIST = "ALLOWLIST";
    public static final String RULE_BLOCKLIST = "BLOCKLIST";
    public static final String SUBJECT_IP = "IP";
    public static final String SUBJECT_USER = "USER";
    public static final String SCOPE_NONE = "NONE";
    public static final String SCOPE_IP = "IP";
    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_USER_OR_IP = "USER_OR_IP";
    public static final String PROFILE_NONE = "NONE";
    public static final String PROFILE_JSON_BODY = "JSON_BODY";
    public static final String PROFILE_PAGE_QUERY = "PAGE_QUERY";

    private final GatewayRoutePolicyRepository gatewayRoutePolicyRepository;
    private final GatewayClientRuleRepository gatewayClientRuleRepository;
    private final GatewayRiskAuditRepository gatewayRiskAuditRepository;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public GatewayPolicyService(
            GatewayRoutePolicyRepository gatewayRoutePolicyRepository,
            GatewayClientRuleRepository gatewayClientRuleRepository,
            GatewayRiskAuditRepository gatewayRiskAuditRepository
    ) {
        this.gatewayRoutePolicyRepository = gatewayRoutePolicyRepository;
        this.gatewayClientRuleRepository = gatewayClientRuleRepository;
        this.gatewayRiskAuditRepository = gatewayRiskAuditRepository;
    }

    @Transactional(readOnly = true)
    public Optional<GatewayRoutePolicyEntity> resolvePolicy(String requestPath, String httpMethod) {
        String normalizedMethod = normalize(httpMethod);
        return gatewayRoutePolicyRepository.findByEnabledTrue().stream()
                .filter(policy -> matchesMethod(policy.getHttpMethod(), normalizedMethod))
                .filter(policy -> antPathMatcher.match(policy.getPathPattern(), requestPath))
                .max(Comparator
                        .comparingInt((GatewayRoutePolicyEntity policy) -> policy.getPathPattern().length())
                        .thenComparing(policy -> "*".equals(policy.getHttpMethod()) ? 0 : 1));
    }

    @Transactional(readOnly = true)
    public RuleDecision evaluateClientRules(String userId, String clientIp) {
        List<GatewayClientRuleEntity> activeRules = gatewayClientRuleRepository.findByEnabledTrue().stream()
                .filter(this::isRuleActive)
                .toList();

        boolean allowlistMatched = activeRules.stream().anyMatch(rule ->
                RULE_ALLOWLIST.equalsIgnoreCase(rule.getRuleType()) && matchesSubject(rule, userId, clientIp));
        if (allowlistMatched) {
            return new RuleDecision(true, "ALLOWLIST_MATCHED", null);
        }

        return activeRules.stream()
                .filter(rule -> RULE_BLOCKLIST.equalsIgnoreCase(rule.getRuleType()))
                .filter(rule -> matchesSubject(rule, userId, clientIp))
                .findFirst()
                .map(rule -> new RuleDecision(false, "BLOCKLIST_MATCHED", defaultString(rule.getReason(), "Blocked by gateway client rule")))
                .orElseGet(() -> new RuleDecision(false, null, null));
    }

    @Transactional
    public void recordAudit(
            GatewayRoutePolicyEntity policy,
            String requestPath,
            String userId,
            String authMode,
            String decision,
            String reasonCode,
            String clientIp
    ) {
        String routeCode = policy == null ? "UNMATCHED" : policy.getRouteCode();
        String pathPattern = policy == null ? requestPath : policy.getPathPattern();
        String httpMethod = policy == null ? "*" : policy.getHttpMethod();
        String riskLevel = policy == null ? "LOW" : policy.getRiskLevel();
        gatewayRiskAuditRepository.save(new GatewayRiskAuditEntity(
                null,
                routeCode,
                pathPattern,
                httpMethod,
                userId,
                authMode,
                decision,
                reasonCode,
                riskLevel,
                clientIp,
                TraceIdContext.currentOrGenerate(),
                LocalDateTime.now()
        ));
    }

    public UserContext resolveUserContext(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return new UserContext(null, null);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LocalAccessTokenService.AccessTokenPrincipal accessTokenPrincipal) {
            return new UserContext(accessTokenPrincipal.userId(), accessTokenPrincipal.authMode());
        }
        if (principal instanceof OidcUser oidcUser) {
            return new UserContext(oidcUser.getSubject(), AuthMode.OIDC);
        }
        if (principal instanceof Jwt jwt) {
            return new UserContext(jwt.getSubject(), AuthMode.OIDC);
        }
        return new UserContext(authentication.getName(), null);
    }

    public String resolveRateLimitKey(GatewayRoutePolicyEntity policy, String userId, String clientIp) {
        if (policy == null || SCOPE_NONE.equalsIgnoreCase(policy.getRateLimitScope())) {
            return null;
        }
        String prefix = policy.getRouteCode() + ":";
        return switch (normalize(policy.getRateLimitScope())) {
            case SCOPE_USER -> userId == null ? null : prefix + "USER:" + userId;
            case SCOPE_IP -> clientIp == null ? null : prefix + "IP:" + clientIp;
            case SCOPE_USER_OR_IP -> userId != null ? prefix + "USER:" + userId : clientIp == null ? null : prefix + "IP:" + clientIp;
            default -> null;
        };
    }

    public boolean requiresJsonBody(String method, long contentLength, GatewayRoutePolicyEntity policy) {
        if (!isMutationMethod(method)) {
            return false;
        }
        if (policy != null && PROFILE_JSON_BODY.equalsIgnoreCase(policy.getValidationProfile())) {
            return true;
        }
        return contentLength > 0;
    }

    public boolean validatesPageQuery(GatewayRoutePolicyEntity policy) {
        return policy != null && PROFILE_PAGE_QUERY.equalsIgnoreCase(policy.getValidationProfile());
    }

    public boolean shouldAuditAllow(GatewayRoutePolicyEntity policy) {
        return policy != null && policy.isAuditEnabled();
    }

    public int resolveCapacity(GatewayRoutePolicyEntity policy, int defaultCapacity) {
        return policy == null || policy.getRateLimitCapacity() == null ? defaultCapacity : policy.getRateLimitCapacity();
    }

    public int resolveWindowSeconds(GatewayRoutePolicyEntity policy, int defaultWindowSeconds) {
        return policy == null || policy.getRateLimitWindowSeconds() == null
                ? defaultWindowSeconds
                : policy.getRateLimitWindowSeconds();
    }

    private boolean matchesMethod(String configuredMethod, String requestMethod) {
        return "*".equals(configuredMethod) || normalize(configuredMethod).equals(requestMethod);
    }

    private boolean isRuleActive(GatewayClientRuleEntity rule) {
        return rule.getExpiresAt() == null || rule.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private boolean matchesSubject(GatewayClientRuleEntity rule, String userId, String clientIp) {
        return switch (normalize(rule.getSubjectType())) {
            case SUBJECT_USER -> userId != null && userId.equalsIgnoreCase(rule.getSubjectValue());
            case SUBJECT_IP -> clientIp != null && clientIp.equalsIgnoreCase(rule.getSubjectValue());
            default -> false;
        };
    }

    private boolean isMutationMethod(String method) {
        String normalizedMethod = normalize(method);
        return "POST".equals(normalizedMethod) || "PUT".equals(normalizedMethod) || "PATCH".equals(normalizedMethod);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record RuleDecision(boolean allowlisted, String decision, String reason) {
    }

    public record UserContext(String userId, String authMode) {
    }
}
