package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.AccountCreateRequest;
import com.uscdip.backend.dto.AccountOptionsResponse;
import com.uscdip.backend.dto.AccountPageResponse;
import com.uscdip.backend.dto.AccountPasswordResetRequest;
import com.uscdip.backend.dto.AccountResponse;
import com.uscdip.backend.dto.AccountUpdateRequest;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.AccountManagementService;
import com.uscdip.backend.service.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/auth/admin/accounts")
@AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT")
@Tag(name = "Account Management", description = "平台管理员账号、角色、数据域和本地凭据管理")
public class AccountManagementController {

    private final AccountManagementService accountManagementService;
    private final CurrentUserResolver currentUserResolver;

    public AccountManagementController(AccountManagementService accountManagementService, CurrentUserResolver currentUserResolver) {
        this.accountManagementService = accountManagementService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @Operation(summary = "分页查询账号")
    public ApiResponse<AccountPageResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        return ApiResponse.success(accountManagementService.list(keyword, status, page, pageSize));
    }

    @GetMapping("/options")
    @Operation(summary = "获取账号管理选项")
    public ApiResponse<AccountOptionsResponse> options() {
        return ApiResponse.success(accountManagementService.options());
    }

    @PostMapping
    @Operation(summary = "创建本地登录账号")
    public ApiResponse<AccountResponse> create(
            @Valid @RequestBody AccountCreateRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        String actorUserId = currentUserResolver.requireContext(authentication).userId();
        return ApiResponse.success(accountManagementService.create(
                request,
                actorUserId,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        ));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "更新账号资料、状态、角色和数据域")
    public ApiResponse<AccountResponse> update(
            @PathVariable String userId,
            @Valid @RequestBody AccountUpdateRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        String actorUserId = currentUserResolver.requireContext(authentication).userId();
        return ApiResponse.success(accountManagementService.update(
                userId,
                request,
                actorUserId,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/{userId}/password")
    @Operation(summary = "重置本地登录密码")
    public ApiResponse<AccountResponse> resetPassword(
            @PathVariable String userId,
            @Valid @RequestBody AccountPasswordResetRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        String actorUserId = currentUserResolver.requireContext(authentication).userId();
        return ApiResponse.success(accountManagementService.resetPassword(
                userId,
                request,
                actorUserId,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        ));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
    }
}
