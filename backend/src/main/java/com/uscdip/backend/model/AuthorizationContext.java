package com.uscdip.backend.model;

import java.util.List;

public record AuthorizationContext(
        String userId,
        String username,
        List<String> roleCodes,
        List<String> permissionCodes,
        ResolvedDataScope dataScope,
        List<String> topicPatterns,
        boolean platformAdmin
) {
}
