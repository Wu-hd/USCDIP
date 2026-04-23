package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.WorkOrderCloseRequest;
import com.uscdip.backend.dto.WorkOrderCompleteRequest;
import com.uscdip.backend.dto.WorkOrderCreateRequest;
import com.uscdip.backend.dto.WorkOrderDispatchRequest;
import com.uscdip.backend.dto.WorkOrderResponse;
import com.uscdip.backend.dto.WorkOrderWritebackRequest;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.WorkOrderService;
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
@RequestMapping("/api/workorders")
@Tag(name = "Work Orders", description = "B-22 工单服务与闭环回写接口")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final CurrentUserResolver currentUserResolver;

    public WorkOrderController(WorkOrderService workOrderService, CurrentUserResolver currentUserResolver) {
        this.workOrderService = workOrderService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:READ")
    @Operation(summary = "分页查询工单")
    public ApiResponse<PageResponse<WorkOrderResponse>> listWorkOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String incidentId,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String workOrderType
    ) {
        return ApiResponse.success(workOrderService.listWorkOrders(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                status,
                incidentId,
                assignee,
                workOrderType
        ));
    }

    @GetMapping("/{workOrderId}")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:READ")
    @Operation(summary = "查询工单详情")
    public ApiResponse<WorkOrderResponse> getWorkOrderDetail(Authentication authentication, @PathVariable String workOrderId) {
        return ApiResponse.success(workOrderService.getWorkOrderDetail(
                currentUserResolver.requireContext(authentication),
                workOrderId
        ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:DISPATCH")
    @Operation(summary = "基于事件创建工单")
    public ApiResponse<WorkOrderResponse> createWorkOrder(
            Authentication authentication,
            @Valid @RequestBody WorkOrderCreateRequest request
    ) {
        return ApiResponse.success(workOrderService.createWorkOrder(
                currentUserResolver.requireContext(authentication),
                request
        ));
    }

    @PostMapping("/{workOrderId}/dispatch")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:DISPATCH")
    @Operation(summary = "派发工单")
    public ApiResponse<WorkOrderResponse> dispatchWorkOrder(
            Authentication authentication,
            @PathVariable String workOrderId,
            @Valid @RequestBody WorkOrderDispatchRequest request
    ) {
        return ApiResponse.success(workOrderService.dispatchWorkOrder(
                currentUserResolver.requireContext(authentication),
                workOrderId,
                request
        ));
    }

    @PostMapping("/{workOrderId}/accept")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:READ")
    @Operation(summary = "接单")
    public ApiResponse<WorkOrderResponse> acceptWorkOrder(Authentication authentication, @PathVariable String workOrderId) {
        return ApiResponse.success(workOrderService.acceptWorkOrder(
                currentUserResolver.requireContext(authentication),
                workOrderId
        ));
    }

    @PostMapping("/{workOrderId}/transfer")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:DISPATCH")
    @Operation(summary = "转派工单")
    public ApiResponse<WorkOrderResponse> transferWorkOrder(
            Authentication authentication,
            @PathVariable String workOrderId,
            @Valid @RequestBody WorkOrderDispatchRequest request
    ) {
        return ApiResponse.success(workOrderService.transferWorkOrder(
                currentUserResolver.requireContext(authentication),
                workOrderId,
                request
        ));
    }

    @PostMapping("/{workOrderId}/complete")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:READ")
    @Operation(summary = "完成工单")
    public ApiResponse<WorkOrderResponse> completeWorkOrder(
            Authentication authentication,
            @PathVariable String workOrderId,
            @Valid @RequestBody WorkOrderCompleteRequest request
    ) {
        return ApiResponse.success(workOrderService.completeWorkOrder(
                currentUserResolver.requireContext(authentication),
                workOrderId,
                request
        ));
    }

    @PostMapping("/{workOrderId}/close")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:DISPATCH")
    @Operation(summary = "关闭工单")
    public ApiResponse<WorkOrderResponse> closeWorkOrder(
            Authentication authentication,
            @PathVariable String workOrderId,
            @Valid @RequestBody WorkOrderCloseRequest request
    ) {
        return ApiResponse.success(workOrderService.closeWorkOrder(
                currentUserResolver.requireContext(authentication),
                workOrderId,
                request
        ));
    }

    @PostMapping("/{workOrderId}/writeback")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:DISPATCH")
    @Operation(summary = "记录误报、漏报或其他回写")
    public ApiResponse<WorkOrderResponse> writebackWorkOrder(
            Authentication authentication,
            @PathVariable String workOrderId,
            @Valid @RequestBody WorkOrderWritebackRequest request
    ) {
        return ApiResponse.success(workOrderService.writebackWorkOrder(
                currentUserResolver.requireContext(authentication),
                workOrderId,
                request
        ));
    }
}
