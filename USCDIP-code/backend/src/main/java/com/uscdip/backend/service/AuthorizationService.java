package com.uscdip.backend.service;

import com.uscdip.backend.dto.AuthzCheckRequest;
import com.uscdip.backend.dto.AuthzCheckResult;
import com.uscdip.backend.entity.PermissionEntity;
import com.uscdip.backend.entity.RoleEntity;
import com.uscdip.backend.entity.TopicScopeRuleEntity;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.entity.UserDataScopeEntity;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ResolvedDataScope;
import com.uscdip.backend.repository.PermissionRepository;
import com.uscdip.backend.repository.RolePermissionRepository;
import com.uscdip.backend.repository.RoleRepository;
import com.uscdip.backend.repository.TopicScopeRuleRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import com.uscdip.backend.repository.UserDataScopeRepository;
import com.uscdip.backend.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthorizationService {

    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String ROLE_REGIONAL_DISPATCHER = "REGIONAL_DISPATCHER";
    private static final String ROLE_INSPECTOR = "INSPECTOR";
    private static final String ROLE_ALGORITHM_ENGINEER = "ALGORITHM_ENGINEER";
    private static final String ROLE_LEADER_READONLY = "LEADER_READONLY";
    private static final String ROLE_BREAK_GLASS_COMMAND = "BREAK_GLASS_COMMAND";

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserDataScopeRepository userDataScopeRepository;
    private final TopicScopeRuleRepository topicScopeRuleRepository;

    public AuthorizationService(
            UserAccountRepository userAccountRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            UserDataScopeRepository userDataScopeRepository,
            TopicScopeRuleRepository topicScopeRuleRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userDataScopeRepository = userDataScopeRepository;
        this.topicScopeRuleRepository = topicScopeRuleRepository;
    }

    public Optional<AuthorizationContext> getAuthorizationContext(String userId) {
        return userAccountRepository.findById(userId).map(user -> {
            List<String> roleCodes = findRoleCodes(userId);
            List<String> permissionCodes = findPermissionCodes(roleCodes);
            List<String> regionScopes = findScopeValues(userId, "REGION");
            List<String> topicPatterns = resolveTopicPatterns(user, roleCodes, regionScopes);
            return new AuthorizationContext(
                    user.getUserId(),
                    normalize(user.getUsername()),
                    roleCodes,
                    permissionCodes,
                    resolveDataScope(user, roleCodes, regionScopes),
                    topicPatterns,
                    roleCodes.contains(ROLE_PLATFORM_ADMIN)
            );
        });
    }

    public Optional<Map<String, Object>> getUserSnapshot(String userId) {
        return getAuthorizationContext(userId).map(context -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", context.userId());
            payload.put("username", normalizeOrNull(context.username()));
            payload.put("displayName", userAccountRepository.findById(userId).map(UserAccountEntity::getDisplayName).orElse(null));
            payload.put("primaryRegionId", userAccountRepository.findById(userId).map(UserAccountEntity::getPrimaryRegionId).orElse(null));
            payload.put("status", userAccountRepository.findById(userId).map(UserAccountEntity::getStatus).orElse(null));
            payload.put("roleCodes", context.roleCodes());
            payload.put("permissionCodes", context.permissionCodes());
            payload.put("dataScopeRule", context.dataScope().rule());
            payload.put("authorizedRegions", context.dataScope().authorizedRegions());
            payload.put("authorizedAssignees", context.dataScope().authorizedAssignees());
            payload.put("dataViewConstraint", context.dataScope().dataViewConstraint());
            payload.put("topicPatterns", context.topicPatterns());
            payload.put("maskedFeatureOnly", "MASKED_FEATURE".equalsIgnoreCase(context.dataScope().dataViewConstraint()));
            return payload;
        });
    }

    public AuthzCheckResult check(AuthzCheckRequest request) {
        if (request == null || isBlank(request.getUserId())) {
            return denied("INVALID_REQUEST", null, Collections.emptyList(), false, false, false, false);
        }

        Optional<AuthorizationContext> contextOptional = getAuthorizationContext(request.getUserId().trim());
        if (contextOptional.isEmpty()) {
            return denied("USER_NOT_FOUND", request.getUserId(), Collections.emptyList(), false, false, false, false);
        }

        AuthorizationContext context = contextOptional.get();
        boolean entryPermission = hasPermission(context, request.getEntryPermission());
        boolean menuPermission = hasPermission(context, request.getMenuPermission());
        boolean dataScope = evaluateDataScope(context, request);
        boolean topicScope = evaluateTopicScope(context, request.getTopic());
        boolean allowed = entryPermission && menuPermission && dataScope && topicScope;

        if (allowed) {
            return AuthzCheckResult.builder()
                    .userId(context.userId())
                    .roleCodes(context.roleCodes())
                    .entryPermission(true)
                    .menuPermission(true)
                    .dataScope(true)
                    .topicScope(true)
                    .allowed(true)
                    .reason("ALLOW")
                    .build();
        }

        String reason;
        if (!entryPermission) {
            reason = "ENTRY_PERMISSION_DENIED";
        } else if (!menuPermission) {
            reason = "MENU_PERMISSION_DENIED";
        } else if (!dataScope) {
            reason = "DATA_SCOPE_DENIED";
        } else {
            reason = "TOPIC_SCOPE_DENIED";
        }

        return AuthzCheckResult.builder()
                .userId(context.userId())
                .roleCodes(context.roleCodes())
                .entryPermission(entryPermission)
                .menuPermission(menuPermission)
                .dataScope(dataScope)
                .topicScope(topicScope)
                .allowed(false)
                .reason(reason)
                .build();
    }

    public Optional<List<String>> getAuthorizedTopics(String userId) {
        return getAuthorizationContext(userId).map(AuthorizationContext::topicPatterns);
    }

    public List<Map<String, Object>> getRoleMatrix() {
        List<RoleEntity> roles = roleRepository.findAll();
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> roleCodes = roles.stream().map(RoleEntity::getRoleCode).toList();
        Map<String, List<String>> rolePermissionCodeMap = rolePermissionRepository.findByRoleCodeIn(roleCodes)
                .stream()
                .collect(Collectors.groupingBy(
                        rolePermission -> normalize(rolePermission.getRoleCode()),
                        Collectors.mapping(rolePermission -> normalize(rolePermission.getPermissionCode()), Collectors.toList())
                ));

        Set<String> allPermissionCodes = rolePermissionCodeMap.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, PermissionEntity> permissionMap = permissionRepository.findByPermissionCodeIn(allPermissionCodes)
                .stream()
                .collect(Collectors.toMap(permission -> normalize(permission.getPermissionCode()), permission -> permission));

        Map<String, List<String>> roleTopicMap = topicScopeRuleRepository.findByRoleCodeIn(roleCodes)
                .stream()
                .collect(Collectors.groupingBy(
                        rule -> normalize(rule.getRoleCode()),
                        Collectors.mapping(TopicScopeRuleEntity::getTopicPattern, Collectors.toList())
                ));

        List<Map<String, Object>> matrix = new ArrayList<>();
        for (RoleEntity role : roles) {
            String roleCode = normalize(role.getRoleCode());
            List<String> permissionCodes = rolePermissionCodeMap.getOrDefault(roleCode, Collections.emptyList());
            List<PermissionEntity> permissions = permissionCodes.stream()
                    .map(permissionMap::get)
                    .filter(permission -> permission != null)
                    .toList();

            List<String> entryPermissions = permissions.stream()
                    .filter(permission -> "ENTRY".equalsIgnoreCase(permission.getPermissionType()))
                    .map(PermissionEntity::getPermissionCode)
                    .sorted()
                    .toList();

            List<String> menuPermissions = permissions.stream()
                    .filter(permission -> "MENU".equalsIgnoreCase(permission.getPermissionType()))
                    .map(PermissionEntity::getPermissionCode)
                    .sorted()
                    .toList();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleCode", role.getRoleCode());
            row.put("roleName", role.getRoleName());
            row.put("readOnly", role.getReadOnly());
            row.put("entryPermissions", entryPermissions);
            row.put("menuPermissions", menuPermissions);
            row.put("topicPatterns", roleTopicMap.getOrDefault(roleCode, Collections.emptyList()));
            row.put("dataScopeRule", inferDataScopeRule(roleCode));
            row.put("dataViewConstraint", inferDataViewConstraint(roleCode));
            matrix.add(row);
        }
        return matrix;
    }

    public boolean hasPermission(AuthorizationContext context, String requestedPermission) {
        return isBlank(requestedPermission) || context.permissionCodes().contains(normalize(requestedPermission));
    }

    public boolean isPlatformAdmin(AuthorizationContext context) {
        return context != null && context.platformAdmin();
    }

    private AuthzCheckResult denied(
            String reason,
            String userId,
            List<String> roleCodes,
            boolean entryPermission,
            boolean menuPermission,
            boolean dataScope,
            boolean topicScope
    ) {
        return AuthzCheckResult.builder()
                .userId(userId)
                .roleCodes(roleCodes)
                .entryPermission(entryPermission)
                .menuPermission(menuPermission)
                .dataScope(dataScope)
                .topicScope(topicScope)
                .allowed(false)
                .reason(reason)
                .build();
    }

    private ResolvedDataScope resolveDataScope(UserAccountEntity user, List<String> roleCodes, List<String> regionScopes) {
        List<String> authorizedAssignees = new ArrayList<>();
        if (roleCodes.contains(ROLE_INSPECTOR)) {
            authorizedAssignees.add(normalize(user.getUserId()));
            authorizedAssignees.add(normalize(user.getUsername()));
        }
        if (roleCodes.contains(ROLE_PLATFORM_ADMIN)) {
            authorizedAssignees.add("*");
        }
        String rule = roleCodes.stream()
                .map(this::inferDataScopeRule)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("CUSTOM");
        return new ResolvedDataScope(rule, regionScopes, authorizedAssignees, inferDataViewConstraint(rule));
    }

    private List<String> findRoleCodes(String userId) {
        return userRoleRepository.findByUserId(userId)
                .stream()
                .map(mapping -> normalize(mapping.getRoleCode()))
                .distinct()
                .toList();
    }

    private List<String> findPermissionCodes(List<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return rolePermissionRepository.findByRoleCodeIn(roleCodes)
                .stream()
                .map(mapping -> normalize(mapping.getPermissionCode()))
                .distinct()
                .toList();
    }

    private List<String> findScopeValues(String userId, String scopeType) {
        return userDataScopeRepository.findByUserId(userId)
                .stream()
                .filter(scope -> scopeType.equalsIgnoreCase(scope.getScopeType()))
                .map(UserDataScopeEntity::getScopeValue)
                .map(this::normalize)
                .distinct()
                .toList();
    }

    private boolean evaluateDataScope(AuthorizationContext context, AuthzCheckRequest request) {
        ResolvedDataScope dataScope = context.dataScope();
        String regionId = normalize(request.getRegionId());
        String assignee = normalize(request.getAssignee());
        String dataView = normalize(request.getDataView());
        return switch (dataScope.rule()) {
            case "ALL" -> true;
            case "REGION_ONLY" -> !isBlank(regionId) && dataScope.authorizedRegions().contains(regionId);
            case "ASSIGNEE_ONLY" -> !isBlank(assignee) && dataScope.authorizedAssignees().stream().anyMatch(scope -> scope.equalsIgnoreCase(assignee));
            case "MASKED_FEATURE_ONLY" -> "MASKED_FEATURE".equalsIgnoreCase(dataView);
            case "AGGREGATED_READ_ONLY" -> isReadPermission(request.getMenuPermission())
                    && ("AGGREGATED".equalsIgnoreCase(dataView) || "MASKED_FEATURE".equalsIgnoreCase(dataView));
            default -> false;
        };
    }

    private boolean evaluateTopicScope(AuthorizationContext context, String topic) {
        if (isBlank(topic) || context.platformAdmin()) {
            return true;
        }
        String normalizedTopic = topic.trim();
        return context.topicPatterns().stream().anyMatch(pattern -> topicMatches(pattern, normalizedTopic));
    }

    private List<String> resolveTopicPatterns(UserAccountEntity user, List<String> roleCodes, List<String> regionScopes) {
        if (roleCodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<TopicScopeRuleEntity> rules = topicScopeRuleRepository.findByRoleCodeIn(roleCodes);
        Set<String> patterns = new LinkedHashSet<>();

        for (TopicScopeRuleEntity rule : rules) {
            String rawPattern = rule.getTopicPattern();
            if (isBlank(rawPattern)) {
                continue;
            }

            if (rawPattern.contains("{regionId}")) {
                for (String regionScope : regionScopes) {
                    patterns.add(rawPattern.replace("{regionId}", regionScope));
                }
                continue;
            }

            patterns.add(rawPattern
                    .replace("{userId}", user.getUserId())
                    .replace("{username}", user.getUsername()));
        }
        return new ArrayList<>(patterns);
    }

    private boolean topicMatches(String pattern, String topic) {
        StringBuilder regexBuilder = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == '#') {
                regexBuilder.append(".*");
            } else if (ch == '*') {
                regexBuilder.append("[^.]+");
            } else if (ch == '.') {
                regexBuilder.append("\\.");
            } else {
                regexBuilder.append(ch);
            }
        }
        return topic.matches(regexBuilder.toString());
    }

    private boolean isReadPermission(String menuPermission) {
        return !isBlank(menuPermission) && normalize(menuPermission).endsWith(":READ");
    }

    private String inferDataScopeRule(String roleCode) {
        return switch (normalize(roleCode)) {
            case ROLE_PLATFORM_ADMIN -> "ALL";
            case ROLE_REGIONAL_DISPATCHER -> "REGION_ONLY";
            case ROLE_BREAK_GLASS_COMMAND -> "REGION_ONLY";
            case ROLE_INSPECTOR -> "ASSIGNEE_ONLY";
            case ROLE_ALGORITHM_ENGINEER -> "MASKED_FEATURE_ONLY";
            case ROLE_LEADER_READONLY -> "AGGREGATED_READ_ONLY";
            default -> "CUSTOM";
        };
    }

    private String inferDataViewConstraint(String dataScopeRule) {
        return switch (dataScopeRule) {
            case "ALL", "REGION_ONLY", "ASSIGNEE_ONLY" -> "DETAIL";
            case "MASKED_FEATURE_ONLY" -> "MASKED_FEATURE";
            case "AGGREGATED_READ_ONLY" -> "AGGREGATED";
            default -> "CUSTOM";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
