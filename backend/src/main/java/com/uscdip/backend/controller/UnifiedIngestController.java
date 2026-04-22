package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.ProtocolAdaptRequest;
import com.uscdip.backend.dto.ProtocolAdaptResponse;
import com.uscdip.backend.dto.UnifiedIngestBatchRequest;
import com.uscdip.backend.dto.UnifiedIngestBatchResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.UnifiedIngestService;
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
@RequestMapping("/api/ingest")
@Tag(name = "UnifiedIngest", description = "B-12 统一入站 DTO 与协议适配骨架")
public class UnifiedIngestController {

    private final UnifiedIngestService unifiedIngestService;
    private final CurrentUserResolver currentUserResolver;

    public UnifiedIngestController(UnifiedIngestService unifiedIngestService, CurrentUserResolver currentUserResolver) {
        this.unifiedIngestService = unifiedIngestService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/metrics")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "接收统一入站批次并落库")
    public ResponseEntity<ApiResponse<UnifiedIngestBatchResponse>> ingestMetrics(
            Authentication authentication,
            @Valid @RequestBody UnifiedIngestBatchRequest request
    ) {
        currentUserResolver.requireContext(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(unifiedIngestService.ingestMetrics(request)));
    }

    @PostMapping("/adapt/{protocolType}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "按协议适配样例载荷并落库")
    public ResponseEntity<ApiResponse<ProtocolAdaptResponse>> adaptPayload(
            Authentication authentication,
            @PathVariable String protocolType,
            @Valid @RequestBody ProtocolAdaptRequest request
    ) {
        currentUserResolver.requireContext(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(unifiedIngestService.adaptPayload(protocolType, request)));
    }

    @GetMapping("/batches")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询接入批次")
    public ApiResponse<PageResponse<UnifiedIngestBatchResponse>> getBatches(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(unifiedIngestService.listBatches(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize
        ));
    }

    @GetMapping("/batches/{batchId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询接入批次详情")
    public ApiResponse<UnifiedIngestBatchResponse> getBatchDetail(
            Authentication authentication,
            @PathVariable String batchId
    ) {
        return ApiResponse.success(unifiedIngestService.getBatchDetail(
                currentUserResolver.requireContext(authentication),
                batchId
        ));
    }
}
