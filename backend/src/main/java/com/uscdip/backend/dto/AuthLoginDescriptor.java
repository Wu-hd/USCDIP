package com.uscdip.backend.dto;

import java.time.Instant;

public record AuthLoginDescriptor(
        boolean enabled,
        String registrationId,
        String authorizationUrl,
        String state,
        Instant stateExpiresAt
) {
}
