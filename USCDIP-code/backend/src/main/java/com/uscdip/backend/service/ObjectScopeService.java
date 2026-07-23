package com.uscdip.backend.service;

import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.ResolvedDataScope;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ObjectScopeService {

    public static final String OBJECT_NODE = "NODE";
    public static final String OBJECT_SEGMENT = "SEGMENT";
    public static final String OBJECT_FACILITY = "FACILITY";
    public static final String OBJECT_DEVICE = "DEVICE";
    public static final String OBJECT_STATION = "STATION";
    public static final String OBJECT_INCIDENT = "INCIDENT";
    public static final String OBJECT_WORK_ORDER = "WORK_ORDER";
    public static final String OBJECT_MODEL_RESULT = "MODEL_RESULT";
    public static final String LEVEL_DETAIL = "DETAIL";
    public static final String LEVEL_MASKED = "MASKED";
    public static final String LEVEL_AGGREGATED = "AGGREGATED";

    private final ObjectScopeBindingRepository objectScopeBindingRepository;

    public ObjectScopeService(ObjectScopeBindingRepository objectScopeBindingRepository) {
        this.objectScopeBindingRepository = objectScopeBindingRepository;
    }

    public void requireAccess(AuthorizationContext context, String objectType, String objectId, String menuPermission) {
        ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId(normalize(objectType), objectId)
                .orElseThrow(() -> denied("Object scope binding is missing for " + objectType + ":" + objectId));
        if (!isAccessible(context, binding, menuPermission)) {
            throw denied(ErrorCode.DATA_SCOPE_DENIED.defaultMessage());
        }
    }

    public Set<String> filterAccessibleIds(AuthorizationContext context, String objectType, Collection<String> objectIds, String menuPermission) {
        if (objectIds == null || objectIds.isEmpty()) {
            return Collections.emptySet();
        }
        return objectScopeBindingRepository.findByObjectTypeAndObjectIdIn(normalize(objectType), objectIds).stream()
                .filter(binding -> isAccessible(context, binding, menuPermission))
                .map(ObjectScopeBindingEntity::getObjectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Map<String, Long> computeAccessibleCounts(AuthorizationContext context) {
        Map<String, Long> grouped = objectScopeBindingRepository.findAll().stream()
                .filter(binding -> isAccessible(context, binding, "MENU:ASSET:READ"))
                .collect(Collectors.groupingBy(binding -> toDictionaryKey(binding.getObjectType()), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : List.of("node", "segment", "facility", "device", "station", "incident", "work_order", "model_result")) {
            result.put(key, grouped.getOrDefault(key, 0L));
        }
        return result;
    }

    public boolean isAccessible(AuthorizationContext context, ObjectScopeBindingEntity binding, String menuPermission) {
        if (context.platformAdmin()) {
            return true;
        }
        ResolvedDataScope dataScope = context.dataScope();
        if (dataScope == null || dataScope.rule() == null) {
            return false;
        }
        return switch (dataScope.rule()) {
            case "REGION_ONLY" -> binding.getRegionId() != null && dataScope.authorizedRegions().contains(binding.getRegionId().trim().toUpperCase());
            case "ASSIGNEE_ONLY" -> matchesAssignee(context, binding);
            case "MASKED_FEATURE_ONLY" -> LEVEL_MASKED.equalsIgnoreCase(binding.getScopeLevel());
            case "AGGREGATED_READ_ONLY" -> menuPermission != null
                    && menuPermission.trim().toUpperCase().endsWith(":READ")
                    && (LEVEL_AGGREGATED.equalsIgnoreCase(binding.getScopeLevel()) || LEVEL_MASKED.equalsIgnoreCase(binding.getScopeLevel()));
            case "ALL" -> true;
            default -> false;
        };
    }

    private boolean matchesAssignee(AuthorizationContext context, ObjectScopeBindingEntity binding) {
        String ownerUserId = binding.getOwnerUserId() == null ? "" : binding.getOwnerUserId().trim().toUpperCase();
        String ownerUsername = binding.getOwnerUsername() == null ? "" : binding.getOwnerUsername().trim().toUpperCase();
        return ownerUserId.equalsIgnoreCase(context.userId())
                || ownerUsername.equalsIgnoreCase(context.username())
                || context.dataScope().authorizedAssignees().stream().anyMatch(scope ->
                scope.equalsIgnoreCase(ownerUserId) || scope.equalsIgnoreCase(ownerUsername));
    }

    private String toDictionaryKey(String objectType) {
        return switch (normalize(objectType)) {
            case OBJECT_NODE -> "node";
            case OBJECT_SEGMENT -> "segment";
            case OBJECT_FACILITY -> "facility";
            case OBJECT_DEVICE -> "device";
            case OBJECT_STATION -> "station";
            case OBJECT_INCIDENT -> "incident";
            case OBJECT_WORK_ORDER -> "work_order";
            case OBJECT_MODEL_RESULT -> "model_result";
            default -> objectType.toLowerCase();
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private AuthFlowException denied(String message) {
        return new AuthFlowException(ErrorCode.DATA_SCOPE_DENIED, HttpStatus.FORBIDDEN, message);
    }
}
