package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.AuditLogResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.UnifiedAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Unified Audit Logs", description = "B-27 统一审计日志服务")
public class AuditLogController {

    private final UnifiedAuditService unifiedAuditService;

    public AuditLogController(UnifiedAuditService unifiedAuditService) {
        this.unifiedAuditService = unifiedAuditService;
    }

    @GetMapping
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT")
    @Operation(summary = "分页查询统一审计日志")
    public ApiResponse<PageResponse<AuditLogResponse>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String eventCategory,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String authMode,
            @RequestParam(required = false) String emergencyAccountId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "false") boolean emergencyOnly
    ) {
        return ApiResponse.success(unifiedAuditService.listAuditLogs(
                page,
                pageSize,
                eventCategory,
                eventType,
                sourceModule,
                actorUserId,
                authMode,
                emergencyAccountId,
                outcome,
                riskLevel,
                objectType,
                objectId,
                traceId,
                from,
                to,
                emergencyOnly
        ));
    }

    @GetMapping("/break-glass-report")
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT")
    @Operation(summary = "查询应急旁路专项审计报表")
    public ApiResponse<PageResponse<AuditLogResponse>> breakGlassReport(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String emergencyAccountId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.success(unifiedAuditService.listAuditLogs(
                page,
                pageSize,
                null,
                null,
                null,
                actorUserId,
                null,
                emergencyAccountId,
                outcome,
                null,
                null,
                null,
                traceId,
                from,
                to,
                true
        ));
    }

    @GetMapping("/{auditId}")
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT")
    @Operation(summary = "查询统一审计日志详情")
    public ApiResponse<AuditLogResponse> getAuditLog(@PathVariable String auditId) {
        return ApiResponse.success(unifiedAuditService.getAuditLog(auditId));
    }
}
