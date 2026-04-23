package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.AlertEvaluateRequest;
import com.uscdip.backend.dto.AlertEvaluateResponse;
import com.uscdip.backend.dto.AlertCaseResponse;
import com.uscdip.backend.dto.AlertPolicyResponse;
import com.uscdip.backend.dto.AlertRecordResponse;
import com.uscdip.backend.dto.AlertRuleResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.AlertCaseLifecycleService;
import com.uscdip.backend.service.AlertRuleEngineService;
import com.uscdip.backend.service.CurrentUserResolver;
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

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "B-17/B-18 告警规则引擎与告警去重抑制升级")
public class AlertController {

    private final AlertRuleEngineService alertRuleEngineService;
    private final AlertCaseLifecycleService alertCaseLifecycleService;
    private final CurrentUserResolver currentUserResolver;

    public AlertController(
            AlertRuleEngineService alertRuleEngineService,
            AlertCaseLifecycleService alertCaseLifecycleService,
            CurrentUserResolver currentUserResolver
    ) {
        this.alertRuleEngineService = alertRuleEngineService;
        this.alertCaseLifecycleService = alertCaseLifecycleService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/evaluate")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "手动触发告警规则评估")
    public ResponseEntity<ApiResponse<AlertEvaluateResponse>> evaluate(
            Authentication authentication,
            @Valid @RequestBody AlertEvaluateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(alertRuleEngineService.evaluate(
                currentUserResolver.requireContext(authentication),
                request
        )));
    }

    @GetMapping
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询告警记录")
    public ApiResponse<PageResponse<AlertRecordResponse>> listAlerts(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String sourceBatchId
    ) {
        return ApiResponse.success(alertRuleEngineService.listAlerts(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                deviceId,
                ruleCode,
                decision,
                severity,
                sourceBatchId
        ));
    }

    @GetMapping("/{alertId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询单条告警记录")
    public ApiResponse<AlertRecordResponse> getAlertDetail(
            Authentication authentication,
            @PathVariable String alertId
    ) {
        return ApiResponse.success(alertRuleEngineService.getAlertDetail(
                currentUserResolver.requireContext(authentication),
                alertId
        ));
    }

    @GetMapping("/cases")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询告警聚合 case")
    public ApiResponse<PageResponse<AlertCaseResponse>> listCases(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String caseStatus,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer escalationLevel
    ) {
        return ApiResponse.success(alertCaseLifecycleService.listCases(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                deviceId,
                ruleCode,
                caseStatus,
                severity,
                escalationLevel
        ));
    }

    @GetMapping("/cases/{caseId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询单条告警聚合 case")
    public ApiResponse<AlertCaseResponse> getCaseDetail(
            Authentication authentication,
            @PathVariable String caseId
    ) {
        return ApiResponse.success(alertCaseLifecycleService.getCaseDetail(
                currentUserResolver.requireContext(authentication),
                caseId
        ));
    }

    @GetMapping("/rules")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询启用中的告警规则")
    public ApiResponse<List<AlertRuleResponse>> getRules(Authentication authentication) {
        currentUserResolver.requireContext(authentication);
        return ApiResponse.success(alertRuleEngineService.listEnabledRules());
    }

    @GetMapping("/policies")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询告警去重抑制升级策略")
    public ApiResponse<List<AlertPolicyResponse>> getPolicies(Authentication authentication) {
        currentUserResolver.requireContext(authentication);
        return ApiResponse.success(alertCaseLifecycleService.listPolicies());
    }
}
