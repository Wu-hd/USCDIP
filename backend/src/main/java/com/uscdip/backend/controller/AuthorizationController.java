package com.uscdip.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.uscdip.backend.dto.AuthzCheckRequest;
import com.uscdip.backend.dto.AuthzCheckResult;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.AuthzMatrixSpecService;
import com.uscdip.backend.service.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/authz")
public class AuthorizationController {

    private final AuthorizationService authorizationService;
    private final AuthzMatrixSpecService authzMatrixSpecService;

    public AuthorizationController(
            AuthorizationService authorizationService,
            AuthzMatrixSpecService authzMatrixSpecService
    ) {
        this.authorizationService = authorizationService;
        this.authzMatrixSpecService = authzMatrixSpecService;
    }

    @GetMapping("/matrix-spec")
    public ApiResponse<JsonNode> getMatrixSpec() {
        return ApiResponse.success(authzMatrixSpecService.getMatrixSpec());
    }

    @GetMapping("/matrix")
    public ApiResponse<List<Map<String, Object>>> getRoleMatrix() {
        return ApiResponse.success(authorizationService.getRoleMatrix());
    }

    @GetMapping("/users/{userId}/snapshot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserSnapshot(@PathVariable String userId) {
        return authorizationService.getUserSnapshot(userId)
                .map(snapshot -> ResponseEntity.ok(ApiResponse.success(snapshot)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure("USER_NOT_FOUND", "User not found: " + userId, null)));
    }

    @GetMapping("/users/{userId}/topics")
    public ResponseEntity<ApiResponse<List<String>>> getAuthorizedTopics(@PathVariable String userId) {
        return authorizationService.getAuthorizedTopics(userId)
                .map(topics -> ResponseEntity.ok(ApiResponse.success(topics)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure("USER_NOT_FOUND", "User not found: " + userId, null)));
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<AuthzCheckResult>> checkAuthorization(@RequestBody AuthzCheckRequest request) {
        AuthzCheckResult result = authorizationService.check(request);
        if (result.isAllowed()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(
                        false,
                        result,
                        new ApiResponse.ApiError(result.getReason(), "Authorization denied"),
                        null
                ));
    }
}
