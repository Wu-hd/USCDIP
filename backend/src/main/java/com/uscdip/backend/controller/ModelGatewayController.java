package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.ModelGrayReleaseRequest;
import com.uscdip.backend.dto.ModelInferRequest;
import com.uscdip.backend.dto.ModelInvocationResponse;
import com.uscdip.backend.dto.ModelRegisterRequest;
import com.uscdip.backend.dto.ModelResponse;
import com.uscdip.backend.dto.ModelRollbackRequest;
import com.uscdip.backend.dto.ModelVersionCreateRequest;
import com.uscdip.backend.dto.ModelVersionResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.ModelGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/models")
@Tag(name = "Model Gateway", description = "B-25 模型注册、版本、灰度、回退与规则兜底")
public class ModelGatewayController {

    private final ModelGatewayService modelGatewayService;
    private final CurrentUserResolver currentUserResolver;

    public ModelGatewayController(ModelGatewayService modelGatewayService, CurrentUserResolver currentUserResolver) {
        this.modelGatewayService = modelGatewayService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:READ")
    @Operation(summary = "分页查询模型")
    public ApiResponse<PageResponse<ModelResponse>> listModels(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(modelGatewayService.listModels(page, pageSize, status));
    }

    @GetMapping("/{modelCode}")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:READ")
    @Operation(summary = "查询模型详情")
    public ApiResponse<ModelResponse> getModelDetail(@PathVariable String modelCode) {
        return ApiResponse.success(modelGatewayService.getModelDetail(modelCode));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "注册模型")
    public ApiResponse<ModelResponse> registerModel(
            Authentication authentication,
            @Valid @RequestBody ModelRegisterRequest request
    ) {
        return ApiResponse.success(modelGatewayService.registerModel(
                currentUserResolver.requireContext(authentication),
                request
        ));
    }

    @PostMapping("/{modelCode}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "创建模型版本")
    public ApiResponse<ModelVersionResponse> createVersion(
            Authentication authentication,
            @PathVariable String modelCode,
            @Valid @RequestBody ModelVersionCreateRequest request
    ) {
        return ApiResponse.success(modelGatewayService.createVersion(
                currentUserResolver.requireContext(authentication),
                modelCode,
                request
        ));
    }

    @PostMapping("/{modelCode}/versions/{versionNo}/gray")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "灰度发布模型版本")
    public ApiResponse<ModelVersionResponse> grayRelease(
            Authentication authentication,
            @PathVariable String modelCode,
            @PathVariable String versionNo,
            @Valid @RequestBody ModelGrayReleaseRequest request
    ) {
        return ApiResponse.success(modelGatewayService.grayRelease(
                currentUserResolver.requireContext(authentication),
                modelCode,
                versionNo,
                request
        ));
    }

    @PostMapping("/{modelCode}/versions/{versionNo}/activate")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "激活模型版本")
    public ApiResponse<ModelVersionResponse> activateVersion(
            Authentication authentication,
            @PathVariable String modelCode,
            @PathVariable String versionNo
    ) {
        return ApiResponse.success(modelGatewayService.activateVersion(
                currentUserResolver.requireContext(authentication),
                modelCode,
                versionNo
        ));
    }

    @PostMapping("/{modelCode}/rollback")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "回退模型版本")
    public ApiResponse<ModelVersionResponse> rollback(
            Authentication authentication,
            @PathVariable String modelCode,
            @RequestBody(required = false) ModelRollbackRequest request
    ) {
        return ApiResponse.success(modelGatewayService.rollback(
                currentUserResolver.requireContext(authentication),
                modelCode,
                request
        ));
    }

    @PostMapping("/{modelCode}/infer")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "模型推理并在失败或超时时规则兜底")
    public ApiResponse<ModelInvocationResponse> infer(
            Authentication authentication,
            @PathVariable String modelCode,
            @Valid @RequestBody ModelInferRequest request
    ) {
        return ApiResponse.success(modelGatewayService.infer(
                currentUserResolver.requireContext(authentication),
                modelCode,
                request
        ));
    }
}
