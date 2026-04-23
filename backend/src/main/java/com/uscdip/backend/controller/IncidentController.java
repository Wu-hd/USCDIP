package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.IncidentResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.IncidentEventizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "B-19 告警事件化与最小事件管理接口")
public class IncidentController {

    private final IncidentEventizationService incidentEventizationService;
    private final CurrentUserResolver currentUserResolver;

    public IncidentController(IncidentEventizationService incidentEventizationService, CurrentUserResolver currentUserResolver) {
        this.incidentEventizationService = incidentEventizationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询事件")
    public ApiResponse<PageResponse<IncidentResponse>> listIncidents(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String incidentType
    ) {
        return ApiResponse.success(incidentEventizationService.listIncidents(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                deviceId,
                ruleCode,
                status,
                incidentType
        ));
    }

    @GetMapping("/{incidentId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询单条事件详情")
    public ApiResponse<IncidentResponse> getIncidentDetail(Authentication authentication, @PathVariable String incidentId) {
        return ApiResponse.success(incidentEventizationService.getIncidentDetail(
                currentUserResolver.requireContext(authentication),
                incidentId
        ));
    }

    @PostMapping("/{incidentId}/confirm")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "确认待人工复核事件")
    public ApiResponse<IncidentResponse> confirmIncident(Authentication authentication, @PathVariable String incidentId) {
        return ApiResponse.success(incidentEventizationService.confirmIncident(
                currentUserResolver.requireContext(authentication),
                incidentId
        ));
    }
}
