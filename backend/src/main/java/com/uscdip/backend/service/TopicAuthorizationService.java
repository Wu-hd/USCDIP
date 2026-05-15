package com.uscdip.backend.service;

import com.uscdip.backend.dto.AuthzCheckRequest;
import com.uscdip.backend.dto.TopicAuthorizationRequest;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.TopicAuthorizationResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TopicAuthorizationService {

    private final AuthorizationService authorizationService;

    public TopicAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public List<String> expandAuthorizedTopics(String userId) {
        return authorizationService.getAuthorizedTopics(userId).orElse(List.of());
    }

    public TopicAuthorizationResult checkTopics(AuthorizationContext context, TopicAuthorizationRequest request) {
        List<String> requestedTopics = request.requestedTopics() == null ? List.of() : request.requestedTopics();
        List<String> allowedTopics = new ArrayList<>();
        List<String> deniedTopics = new ArrayList<>();

        for (String topic : requestedTopics) {
            AuthzCheckRequest checkRequest = new AuthzCheckRequest(
                    context.userId(),
                    request.entryPermission(),
                    request.menuPermission(),
                    topic,
                    request.regionId(),
                    request.assignee(),
                    request.dataView()
            );
            boolean allowed = authorizationService.check(checkRequest).isAllowed();
            if (allowed) {
                allowedTopics.add(topic);
            } else {
                deniedTopics.add(topic);
            }
        }
        return new TopicAuthorizationResult(context.userId(), requestedTopics, allowedTopics, deniedTopics, deniedTopics.isEmpty());
    }

    public TopicAuthorizationResult subscribe(AuthorizationContext context, TopicAuthorizationRequest request) {
        TopicAuthorizationResult result = checkTopics(context, request);
        if (!result.allAllowed()) {
            throw new AuthFlowException(
                    ErrorCode.SUBSCRIPTION_NOT_ALLOWED,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.SUBSCRIPTION_NOT_ALLOWED.defaultMessage()
            );
        }
        return result;
    }
}
