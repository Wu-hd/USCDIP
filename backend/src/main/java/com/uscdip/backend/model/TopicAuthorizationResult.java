package com.uscdip.backend.model;

import java.util.List;

public record TopicAuthorizationResult(
        String userId,
        List<String> requestedTopics,
        List<String> allowedTopics,
        List<String> deniedTopics,
        boolean allAllowed
) {
}
