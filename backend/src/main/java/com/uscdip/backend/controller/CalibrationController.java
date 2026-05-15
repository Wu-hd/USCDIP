package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.CalibrationCorrectedPreviewResponse;
import com.uscdip.backend.dto.CalibrationDriftCheckRequest;
import com.uscdip.backend.dto.CalibrationDriftRecordResponse;
import com.uscdip.backend.dto.CalibrationProfileCreateRequest;
import com.uscdip.backend.dto.CalibrationProfileResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.service.CalibrationManagementService;
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
@RequestMapping("/api/calibration")
@Tag(name = "Calibration", description = "B-15 标定与漂移管理接口")
public class CalibrationController {

    private final CalibrationManagementService calibrationManagementService;
    private final CurrentUserResolver currentUserResolver;

    public CalibrationController(
            CalibrationManagementService calibrationManagementService,
            CurrentUserResolver currentUserResolver
    ) {
        this.calibrationManagementService = calibrationManagementService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/devices/{deviceId}/profiles")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "创建设备标定版本档案")
    public ResponseEntity<ApiResponse<CalibrationProfileResponse>> createProfile(
            Authentication authentication,
            @PathVariable String deviceId,
            @Valid @RequestBody CalibrationProfileCreateRequest request
    ) {
        CalibrationProfileResponse response = calibrationManagementService.createProfile(
                currentUserResolver.requireContext(authentication),
                deviceId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/devices/{deviceId}/profiles")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询设备标定版本档案")
    public ApiResponse<List<CalibrationProfileResponse>> getProfiles(
            Authentication authentication,
            @PathVariable String deviceId
    ) {
        return ApiResponse.success(calibrationManagementService.listProfiles(
                currentUserResolver.requireContext(authentication),
                deviceId
        ));
    }

    @GetMapping("/devices/{deviceId}/profiles/active")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询设备当前激活标定版本")
    public ResponseEntity<ApiResponse<CalibrationProfileResponse>> getActiveProfile(
            Authentication authentication,
            @PathVariable String deviceId,
            @RequestParam String metricCode
    ) {
        return calibrationManagementService.getActiveProfile(
                        currentUserResolver.requireContext(authentication),
                        deviceId,
                        metricCode
                )
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(
                                ErrorCode.CALIBRATION_PROFILE_NOT_FOUND,
                                "Active calibration profile not found for device: " + deviceId + ", metricCode: " + metricCode
                        )));
    }

    @PostMapping("/devices/{deviceId}/drift-checks")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:WRITE")
    @Operation(summary = "提交设备漂移复核结果")
    public ResponseEntity<ApiResponse<CalibrationDriftRecordResponse>> createDriftCheck(
            Authentication authentication,
            @PathVariable String deviceId,
            @Valid @RequestBody CalibrationDriftCheckRequest request
    ) {
        CalibrationDriftRecordResponse response = calibrationManagementService.createDriftCheck(
                currentUserResolver.requireContext(authentication),
                deviceId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/devices/{deviceId}/drift-checks")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询设备漂移复核记录")
    public ApiResponse<List<CalibrationDriftRecordResponse>> getDriftChecks(
            Authentication authentication,
            @PathVariable String deviceId
    ) {
        return ApiResponse.success(calibrationManagementService.listDriftChecks(
                currentUserResolver.requireContext(authentication),
                deviceId
        ));
    }

    @GetMapping("/metrics/{sourceRecordId}/corrected")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询采样值的标定校正预览")
    public ApiResponse<CalibrationCorrectedPreviewResponse> getCorrectedPreview(
            Authentication authentication,
            @PathVariable String sourceRecordId,
            @RequestParam(required = false) String profileVersion
    ) {
        return ApiResponse.success(calibrationManagementService.getCorrectedPreview(
                currentUserResolver.requireContext(authentication),
                sourceRecordId,
                profileVersion
        ));
    }
}
