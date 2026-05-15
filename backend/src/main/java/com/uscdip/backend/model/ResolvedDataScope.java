package com.uscdip.backend.model;

import java.util.List;

public record ResolvedDataScope(
        String rule,
        List<String> authorizedRegions,
        List<String> authorizedAssignees,
        String dataViewConstraint
) {
}
