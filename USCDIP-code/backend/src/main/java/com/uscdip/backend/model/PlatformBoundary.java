package com.uscdip.backend.model;

public record PlatformBoundary(
        String platformCode,
        String platformName,
        String routePrefix,
        String permissionPrefix,
        boolean allowWrite,
        String description
) {
}
