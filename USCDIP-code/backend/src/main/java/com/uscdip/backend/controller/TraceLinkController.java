package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.TraceLinkEventResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.TraceLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/traces")
@Tag(name = "Trace Link Events", description = "B-28 Trace 与链路埋点中间件")
public class TraceLinkController {

    private final TraceLinkService traceLinkService;

    public TraceLinkController(TraceLinkService traceLinkService) {
        this.traceLinkService = traceLinkService;
    }

    @GetMapping
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT")
    @Operation(summary = "分页查询链路埋点事件")
    public ApiResponse<PageResponse<TraceLinkEventResponse>> listTraceEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.success(traceLinkService.listTraceEvents(
                page,
                pageSize,
                traceId,
                stage,
                sourceModule,
                objectType,
                objectId,
                status,
                from,
                to
        ));
    }

    @GetMapping("/{traceId}")
    @AuthzGuard(requiredRole = "PLATFORM_ADMIN", entryPermission = "ENTRY:MGMT")
    @Operation(summary = "查询单条 trace 全链路埋点")
    public ApiResponse<List<TraceLinkEventResponse>> getTrace(@PathVariable String traceId) {
        return ApiResponse.success(traceLinkService.getTrace(traceId));
    }
}
