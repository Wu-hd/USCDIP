package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.FeatureMetricViewResponse;
import com.uscdip.backend.dto.FeatureModelResultViewResponse;
import com.uscdip.backend.dto.FeatureViewAuditResponse;
import com.uscdip.backend.dto.FeatureViewGrantRequest;
import com.uscdip.backend.dto.FeatureViewGrantResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.FeatureViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDateTime;

@Validated
@RestController
@RequestMapping("/api/feature-views")
@Tag(name = "Feature Views", description = "B-26 特征视图与算法脱敏接口")
public class FeatureViewController {

    private final FeatureViewService featureViewService;
    private final CurrentUserResolver currentUserResolver;

    public FeatureViewController(FeatureViewService featureViewService, CurrentUserResolver currentUserResolver) {
        this.featureViewService = featureViewService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/metrics")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:READ")
    @Operation(summary = "查询脱敏或授权明细时序特征")
    public ApiResponse<PageResponse<FeatureMetricViewResponse>> listMetricFeatures(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "MASKED") String viewLevel,
            @RequestParam(required = false) String segmentId,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String metricCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        return ApiResponse.success(featureViewService.listMetricFeatures(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                viewLevel,
                segmentId,
                nodeId,
                deviceId,
                metricCode,
                startTime,
                endTime
        ));
    }

    @GetMapping("/model-results")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:READ")
    @Operation(summary = "查询脱敏或授权明细模型结果特征")
    public ApiResponse<PageResponse<FeatureModelResultViewResponse>> listModelResultFeatures(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "MASKED") String viewLevel,
            @RequestParam(required = false) String modelCode,
            @RequestParam(required = false) String segmentId,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(featureViewService.listModelResultFeatures(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                viewLevel,
                modelCode,
                segmentId,
                nodeId,
                status
        ));
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "创建限时明细特征视图授权")
    public ApiResponse<FeatureViewGrantResponse> createGrant(
            Authentication authentication,
            @Valid @RequestBody FeatureViewGrantRequest request
    ) {
        return ApiResponse.success(featureViewService.createGrant(
                currentUserResolver.requireContext(authentication),
                request
        ));
    }

    @PostMapping("/grants/{grantId}/revoke")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "撤销限时明细特征视图授权")
    public ApiResponse<FeatureViewGrantResponse> revokeGrant(
            Authentication authentication,
            @PathVariable String grantId
    ) {
        return ApiResponse.success(featureViewService.revokeGrant(
                currentUserResolver.requireContext(authentication),
                grantId
        ));
    }

    @GetMapping("/audits")
    @AuthzGuard(entryPermission = "ENTRY:DIAG", menuPermission = "MENU:MODEL:WRITE")
    @Operation(summary = "查询特征视图访问审计")
    public ApiResponse<PageResponse<FeatureViewAuditResponse>> listAudits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String decision
    ) {
        return ApiResponse.success(featureViewService.listAudits(page, pageSize, userId, decision));
    }
}
