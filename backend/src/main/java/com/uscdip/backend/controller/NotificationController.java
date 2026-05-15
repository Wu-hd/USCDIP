package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.NotificationConsumeResponse;
import com.uscdip.backend.dto.NotificationResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.NotificationService;
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
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "B-23 稳定业务事件通知服务")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;

    public NotificationController(NotificationService notificationService, CurrentUserResolver currentUserResolver) {
        this.notificationService = notificationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:READ")
    @Operation(summary = "分页查询通知")
    public ApiResponse<PageResponse<NotificationResponse>> listNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String sourceEventId,
            @RequestParam(required = false) String workOrderId
    ) {
        return ApiResponse.success(notificationService.listNotifications(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                status,
                channel,
                recipient,
                sourceEventId,
                workOrderId
        ));
    }

    @GetMapping("/{notificationId}")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:READ")
    @Operation(summary = "查询通知详情")
    public ApiResponse<NotificationResponse> getNotificationDetail(Authentication authentication, @PathVariable String notificationId) {
        return ApiResponse.success(notificationService.getNotificationDetail(
                currentUserResolver.requireContext(authentication),
                notificationId
        ));
    }

    @PostMapping("/consume-outbox")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:DISPATCH")
    @Operation(summary = "手动消费稳定工单 Outbox 事件")
    public ApiResponse<NotificationConsumeResponse> consumeOutbox(Authentication authentication) {
        return ApiResponse.success(notificationService.consumeStableWorkOrderOutbox(
                currentUserResolver.requireContext(authentication)
        ));
    }

    @PostMapping("/{notificationId}/retry")
    @AuthzGuard(entryPermission = "ENTRY:EMGC", menuPermission = "MENU:WORKORDER:DISPATCH")
    @Operation(summary = "重试通知失败通道")
    public ApiResponse<NotificationResponse> retryNotification(Authentication authentication, @PathVariable String notificationId) {
        return ApiResponse.success(notificationService.retryNotification(
                currentUserResolver.requireContext(authentication),
                notificationId
        ));
    }
}
