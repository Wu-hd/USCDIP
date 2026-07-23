package com.uscdip.backend.model;

import java.security.Principal;

public record WebSocketUserPrincipal(
        String name,
        String userId,
        String username,
        String connectionId
) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
