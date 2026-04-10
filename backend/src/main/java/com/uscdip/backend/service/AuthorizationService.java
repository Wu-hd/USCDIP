package com.uscdip.backend.service;

import com.uscdip.backend.dto.AuthzCheckRequest;
import com.uscdip.backend.dto.AuthzCheckResult;
import com.uscdip.backend.entity.PermissionEntity;
import com.uscdip.backend.entity.RoleEntity;
import com.uscdip.backend.entity.TopicScopeRuleEntity;
import com.uscdip.backend.entity.UserAccountEntity;
import com.uscdip.backend.entity.UserDataScopeEntity;
import com.uscdip.backend.repository.PermissionRepository;
import com.uscdip.backend.repository.RolePermissionRepository;
import com.uscdip.backend.repository.RoleRepository;
import com.uscdip.backend.repository.TopicScopeRuleRepository;
import com.uscdip.backend.repository.UserAccountRepository;
import com.uscdip.backend.repository.UserDataScopeRepository;
import com.uscdip.backend.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

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

    public Optional<Map<String, Object>> getUserSnapshot(String userId) {
        return userAccountRepository.findById(userId).map(user -> {
            List<String> roleCodes = findRoleCodes(userId);
            List<String> permissionCodes = findPermissionCodes(roleCodes);
            List<String> regionScopes = findScopeValues(userId, "REGION");
            List<String> topicPatterns = resolveTopicPatterns(user, roleCodes, regionScopes);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", user.getUserId());
            payload.put("username", user.getUsername());
            payload.put("displayName", user.getDisplayName());
            payload.put("primaryRegionId", user.getPrimaryRegionId());
            payload.put("status", user.getStatus());
            payload.put("roleCodes", roleCodes);
            payload.put("permissionCodes", permissionCodes);
            payload.put("regionScopes", regionScopes);
            payload.put("topicPatterns", topicPatterns);
            payload.put("maskedFeatureOnly", roleCodes.contains(ROLE_ALGORITHM_ENGINEER));
            return payload;
        });
    }

    public AuthzCheckResult check(AuthzCheckRequest request) {
        if (request == null || isBlank(request.getUserId())) {
            return denied("INVALID_REQUEST", null, Collections.emptyList(), false, false, false, false);
        }

        Optional<UserAccountEntity> userOptional = userAccountRepository.findById(request.getUserId().trim());
        if (userOptional.isEmpty()) {
            return denied("USER_NOT_FOUND", request.getUserId(), Collections.emptyList(), false, false, false, false);
        }

        UserAccountEntity user = userOptional.get();
        List<String> roleCodes = findRoleCodes(user.getUserId());
        if (roleCodes.isEmpty()) {
            return denied("ROLE_NOT_ASSIGNED", user.getUserId(), roleCodes, false, false, false, false);
        }

        List<String> permissionCodes = findPermissionCodes(roleCodes);
        List<String> regionScopes = findScopeValues(user.getUserId(), "REGION");

        boolean entryPermission = hasPermission(request.getEntryPermission(), permissionCodes);
        boolean menuPermission = hasPermission(request.getMenuPermission(), permissionCodes);
        boolean dataScope = evaluateDataScope(user, roleCodes, regionScopes, request);
        boolean topicScope = evaluateTopicScope(user, roleCodes, regionScopes, request.getTopic());
        boolean allowed = entryPermission && menuPermission && dataScope && topicScope;

        if (allowed) {
            return AuthzCheckResult.builder()
                    .userId(user.getUserId())
                    .roleCodes(roleCodes)
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
                .userId(user.getUserId())
                .roleCodes(roleCodes)
                .entryPermission(entryPermission)
                .menuPermission(menuPermission)
                .dataScope(dataScope)
                .topicScope(topicScope)
                .allowed(false)
                .reason(reason)
                .build();
    }

    public Optional<List<String>> getAuthorizedTopics(String userId) {
        return userAccountRepository.findById(userId)
                .map(user -> {
                    List<String> roleCodes = findRoleCodes(user.getUserId());
                    List<String> regionScopes = findScopeValues(user.getUserId(), "REGION");
                    return resolveTopicPatterns(user, roleCodes, regionScopes);
                });
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
            matrix.add(row);
        }
        return matrix;
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
                .distinct()
                .toList();
    }

    private boolean hasPermission(String requestedPermission, List<String> permissionCodes) {
        return isBlank(requestedPermission) || permissionCodes.contains(normalize(requestedPermission));
    }

    private boolean evaluateDataScope(
            UserAccountEntity user,
            List<String> roleCodes,
            List<String> regionScopes,
            AuthzCheckRequest request
    ) {
        String regionId = normalize(request.getRegionId());
        String assignee = normalize(request.getAssignee());
        String dataView = normalize(request.getDataView());

        for (String roleCode : roleCodes) {
            if (ROLE_PLATFORM_ADMIN.equals(roleCode)) {
                return true;
            }
            if (ROLE_REGIONAL_DISPATCHER.equals(roleCode) && !isBlank(regionId) && regionScopes.contains(regionId)) {
                return true;
            }
            if (ROLE_INSPECTOR.equals(roleCode) && !isBlank(assignee)
                    && (assignee.equalsIgnoreCase(normalize(user.getUsername()))
                    || assignee.equalsIgnoreCase(normalize(user.getUserId())))) {
                return true;
            }
            if (ROLE_ALGORITHM_ENGINEER.equals(roleCode) && "MASKED_FEATURE".equalsIgnoreCase(dataView)) {
                return true;
            }
            if (ROLE_LEADER_READONLY.equals(roleCode)
                    && isReadPermission(request.getMenuPermission())
                    && ("AGGREGATED".equalsIgnoreCase(dataView) || "MASKED_FEATURE".equalsIgnoreCase(dataView))) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateTopicScope(
            UserAccountEntity user,
            List<String> roleCodes,
            List<String> regionScopes,
            String topic
    ) {
        if (isBlank(topic)) {
            return true;
        }
        if (roleCodes.contains(ROLE_PLATFORM_ADMIN)) {
            return true;
        }

        List<String> patterns = resolveTopicPatterns(user, roleCodes, regionScopes);
        String normalizedTopic = topic.trim();
        return patterns.stream().anyMatch(pattern -> topicMatches(pattern, normalizedTopic));
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
        String regex = regexBuilder.toString();
        return topic.matches(regex);
    }

    private boolean isReadPermission(String menuPermission) {
        return !isBlank(menuPermission) && normalize(menuPermission).endsWith(":READ");
    }

    private String inferDataScopeRule(String roleCode) {
        return switch (roleCode) {
            case ROLE_PLATFORM_ADMIN -> "ALL";
            case ROLE_REGIONAL_DISPATCHER -> "REGION_ONLY";
            case ROLE_INSPECTOR -> "ASSIGNEE_ONLY";
            case ROLE_ALGORITHM_ENGINEER -> "MASKED_FEATURE_ONLY";
            case ROLE_LEADER_READONLY -> "AGGREGATED_READ_ONLY";
            default -> "CUSTOM";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
