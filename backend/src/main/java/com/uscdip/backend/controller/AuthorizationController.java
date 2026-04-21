package com.uscdip.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.uscdip.backend.dto.AuthzCheckRequest;
import com.uscdip.backend.dto.AuthzCheckResult;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.AuthzMatrixSpecService;
import com.uscdip.backend.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
@Tag(name = "Authorization", description = "A-04 RBAC + 数据范围 + 订阅范围校验")
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
    @Operation(summary = "查询权限矩阵规范")
    public ApiResponse<JsonNode> getMatrixSpec() {
        return ApiResponse.success(authzMatrixSpecService.getMatrixSpec());
    }

    @GetMapping("/matrix")
    @Operation(summary = "查询角色权限矩阵")
    public ApiResponse<List<Map<String, Object>>> getRoleMatrix() {
        return ApiResponse.success(authorizationService.getRoleMatrix());
    }

    @GetMapping("/users/{userId}/snapshot")
    @Operation(summary = "查询用户权限快照")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserSnapshot(
            @Parameter(description = "用户ID", required = true) @PathVariable String userId
    ) {
        return authorizationService.getUserSnapshot(userId)
                .map(snapshot -> ResponseEntity.ok(ApiResponse.success(snapshot)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.USER_NOT_FOUND, "User not found: " + userId)));
    }

    @GetMapping("/users/{userId}/topics")
    @Operation(summary = "查询用户可订阅 topic 列表")
    public ResponseEntity<ApiResponse<List<String>>> getAuthorizedTopics(
            @Parameter(description = "用户ID", required = true) @PathVariable String userId
    ) {
        return authorizationService.getAuthorizedTopics(userId)
                .map(topics -> ResponseEntity.ok(ApiResponse.success(topics)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.USER_NOT_FOUND, "User not found: " + userId)));
    }

    @PostMapping("/check")
    @Operation(summary = "执行联合鉴权校验")
    public ResponseEntity<ApiResponse<AuthzCheckResult>> checkAuthorization(@Valid @RequestBody AuthzCheckRequest request) {
        AuthzCheckResult result = authorizationService.check(request);
        if (result.isAllowed()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(ErrorCode.FORBIDDEN, "Authorization denied: " + result.getReason(), result));
    }
}
