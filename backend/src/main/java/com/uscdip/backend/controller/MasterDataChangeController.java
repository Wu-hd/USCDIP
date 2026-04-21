package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.MasterChangeDecisionRequest;
import com.uscdip.backend.dto.MasterChangeResponse;
import com.uscdip.backend.dto.MasterChangeSubmitRequest;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.MasterDataChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/master/changes")
@Tag(name = "MasterDataChange", description = "B-09 主数据变更申请与审批")
public class MasterDataChangeController {

    private final MasterDataChangeService masterDataChangeService;
    private final CurrentUserResolver currentUserResolver;

    public MasterDataChangeController(MasterDataChangeService masterDataChangeService, CurrentUserResolver currentUserResolver) {
        this.masterDataChangeService = masterDataChangeService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "提交主数据变更申请")
    public ResponseEntity<ApiResponse<MasterChangeResponse>> submitChange(
            Authentication authentication,
            @Valid @RequestBody MasterChangeSubmitRequest request
    ) {
        MasterChangeResponse response = masterDataChangeService.submitChange(currentUserResolver.requireContext(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "分页查询主数据变更申请")
    public ApiResponse<PageResponse<MasterChangeResponse>> listChanges(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String requestStatus
    ) {
        return ApiResponse.success(masterDataChangeService.listChanges(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                objectType,
                objectId,
                requestStatus
        ));
    }

    @GetMapping("/{requestId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "查询主数据变更申请详情")
    public ResponseEntity<ApiResponse<MasterChangeResponse>> getChange(
            Authentication authentication,
            @PathVariable String requestId
    ) {
        return masterDataChangeService.getChange(currentUserResolver.requireContext(authentication), requestId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.MASTER_CHANGE_NOT_FOUND, "Change request not found: " + requestId)));
    }

    @PostMapping("/{requestId}/approve")
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "审批通过主数据变更申请并生效")
    public ApiResponse<MasterChangeResponse> approveChange(
            Authentication authentication,
            @PathVariable String requestId,
            @RequestBody(required = false) MasterChangeDecisionRequest request
    ) {
        return ApiResponse.success(masterDataChangeService.approveChange(currentUserResolver.requireContext(authentication), requestId, request));
    }

    @PostMapping("/{requestId}/reject")
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "拒绝主数据变更申请")
    public ApiResponse<MasterChangeResponse> rejectChange(
            Authentication authentication,
            @PathVariable String requestId,
            @RequestBody(required = false) MasterChangeDecisionRequest request
    ) {
        return ApiResponse.success(masterDataChangeService.rejectChange(currentUserResolver.requireContext(authentication), requestId, request));
    }
}
