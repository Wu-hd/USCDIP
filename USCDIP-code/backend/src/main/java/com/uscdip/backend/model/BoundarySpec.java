package com.uscdip.backend.model;

import java.util.List;

public record BoundarySpec(
        String version,
        String source,
        List<PlatformBoundary> platforms,
        List<MenuRule> menuRules
) {
}
