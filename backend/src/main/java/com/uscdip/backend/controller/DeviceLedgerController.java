package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.DeviceHeartbeatReportRequest;
import com.uscdip.backend.dto.DeviceLedgerRegisterRequest;
import com.uscdip.backend.dto.DeviceLedgerResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.DeviceLedgerService;
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
@RequestMapping("/api/device-ledger")
@Tag(name = "DeviceLedger", description = "B-11 设备台账与心跳接口")
public class DeviceLedgerController {

    private final DeviceLedgerService deviceLedgerService;
    private final CurrentUserResolver currentUserResolver;

    public DeviceLedgerController(DeviceLedgerService deviceLedgerService, CurrentUserResolver currentUserResolver) {
        this.deviceLedgerService = deviceLedgerService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/devices")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询设备台账")
    public ApiResponse<PageResponse<DeviceLedgerResponse>> getDevices(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String regionId,
            @RequestParam(required = false) String segmentId,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String protocolType,
            @RequestParam(required = false) Boolean calibrationExpired
    ) {
        return ApiResponse.success(deviceLedgerService.getDevices(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                status,
                regionId,
                segmentId,
                nodeId,
                facilityId,
                protocolType,
                calibrationExpired
        ));
    }

    @GetMapping("/devices/{deviceId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询单设备台账详情")
    public ResponseEntity<ApiResponse<DeviceLedgerResponse>> getDeviceDetail(
            Authentication authentication,
            @PathVariable String deviceId
    ) {
        return deviceLedgerService.getDeviceDetail(currentUserResolver.requireContext(authentication), deviceId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.DEVICE_NOT_FOUND, "Device not found: " + deviceId)));
    }

    @PostMapping("/devices/register")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "注册或更新设备台账")
    public ResponseEntity<ApiResponse<DeviceLedgerResponse>> registerDevice(
            Authentication authentication,
            @Valid @RequestBody DeviceLedgerRegisterRequest request
    ) {
        currentUserResolver.requireContext(authentication);
        DeviceLedgerService.UpsertResult result = deviceLedgerService.registerDevice(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result.response()));
    }

    @PostMapping("/devices/{deviceId}/heartbeat")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "上报设备心跳并刷新在线状态")
    public ApiResponse<DeviceLedgerResponse> reportHeartbeat(
            Authentication authentication,
            @PathVariable String deviceId,
            @Valid @RequestBody DeviceHeartbeatReportRequest request
    ) {
        currentUserResolver.requireContext(authentication);
        return ApiResponse.success(deviceLedgerService.reportHeartbeat(deviceId, request));
    }
}
