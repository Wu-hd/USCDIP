package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.MasterDataRecordResponse;
import com.uscdip.backend.dto.StationPipelineLayoutResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.MasterDataQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/master")
@Tag(name = "MasterData", description = "B-08 主数据中心只读查询")
public class MasterDataController {

    private final MasterDataQueryService masterDataQueryService;
    private final CurrentUserResolver currentUserResolver;

    public MasterDataController(MasterDataQueryService masterDataQueryService, CurrentUserResolver currentUserResolver) {
        this.masterDataQueryService = masterDataQueryService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/nodes")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询主数据节点")
    public ApiResponse<PageResponse<MasterDataRecordResponse>> getNodes(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String regionId,
            @RequestParam(required = false) String segmentId
    ) {
        return ApiResponse.success(masterDataQueryService.getNodes(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                status,
                regionId,
                segmentId
        ));
    }

    @GetMapping("/nodes/{nodeId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询主数据节点详情")
    public ResponseEntity<ApiResponse<MasterDataRecordResponse>> getNodeDetail(
            Authentication authentication,
            @PathVariable String nodeId
    ) {
        return masterDataQueryService.getNodeDetail(currentUserResolver.requireContext(authentication), nodeId)
                .map(record -> ResponseEntity.ok(ApiResponse.success(record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.NODE_NOT_FOUND, "Node not found: " + nodeId)));
    }

    @GetMapping("/segments")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询主数据管段")
    public ApiResponse<PageResponse<MasterDataRecordResponse>> getSegments(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String regionId,
            @RequestParam(required = false) String nodeId
    ) {
        return ApiResponse.success(masterDataQueryService.getSegments(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                status,
                regionId,
                nodeId
        ));
    }

    @GetMapping("/segments/{segmentId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询主数据管段详情")
    public ResponseEntity<ApiResponse<MasterDataRecordResponse>> getSegmentDetail(
            Authentication authentication,
            @PathVariable String segmentId
    ) {
        return masterDataQueryService.getSegmentDetail(currentUserResolver.requireContext(authentication), segmentId)
                .map(record -> ResponseEntity.ok(ApiResponse.success(record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.SEGMENT_NOT_FOUND, "Segment not found: " + segmentId)));
    }

    @GetMapping("/facilities")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询主数据附属设施")
    public ApiResponse<PageResponse<MasterDataRecordResponse>> getFacilities(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String regionId,
            @RequestParam(required = false) String segmentId,
            @RequestParam(required = false) String nodeId
    ) {
        return ApiResponse.success(masterDataQueryService.getFacilities(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                status,
                regionId,
                segmentId,
                nodeId
        ));
    }

    @GetMapping("/facilities/{facilityId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询主数据附属设施详情")
    public ResponseEntity<ApiResponse<MasterDataRecordResponse>> getFacilityDetail(
            Authentication authentication,
            @PathVariable String facilityId
    ) {
        return masterDataQueryService.getFacilityDetail(currentUserResolver.requireContext(authentication), facilityId)
                .map(record -> ResponseEntity.ok(ApiResponse.success(record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND, "Facility not found: " + facilityId)));
    }

    @GetMapping("/devices")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询主数据设备")
    public ApiResponse<PageResponse<MasterDataRecordResponse>> getDevices(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String regionId,
            @RequestParam(required = false) String segmentId,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) String facilityId
    ) {
        return ApiResponse.success(masterDataQueryService.getDevices(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                status,
                regionId,
                segmentId,
                nodeId,
                facilityId
        ));
    }

    @GetMapping("/devices/{deviceId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询主数据设备详情")
    public ResponseEntity<ApiResponse<MasterDataRecordResponse>> getDeviceDetail(
            Authentication authentication,
            @PathVariable String deviceId
    ) {
        return masterDataQueryService.getDeviceDetail(currentUserResolver.requireContext(authentication), deviceId)
                .map(record -> ResponseEntity.ok(ApiResponse.success(record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND, "Device not found: " + deviceId)));
    }

    @GetMapping("/stations")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询监测站点")
    public ApiResponse<PageResponse<MasterDataRecordResponse>> getStations(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String regionId,
            @RequestParam(required = false) String provinceAdcode,
            @RequestParam(required = false) String cityAdcode
    ) {
        return ApiResponse.success(masterDataQueryService.getStations(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                status,
                regionId,
                provinceAdcode,
                cityAdcode
        ));
    }

    @GetMapping("/stations/{stationId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询监测站点详情")
    public ResponseEntity<ApiResponse<MasterDataRecordResponse>> getStationDetail(
            Authentication authentication,
            @PathVariable String stationId
    ) {
        return masterDataQueryService.getStationDetail(currentUserResolver.requireContext(authentication), stationId)
                .map(record -> ResponseEntity.ok(ApiResponse.success(record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND, "Station not found: " + stationId)));
    }

    @GetMapping("/stations/{stationId}/pipeline-layout")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询监测站点管网布局")
    public ResponseEntity<ApiResponse<StationPipelineLayoutResponse>> getStationPipelineLayout(
            Authentication authentication,
            @PathVariable String stationId
    ) {
        return masterDataQueryService.getStationPipelineLayout(currentUserResolver.requireContext(authentication), stationId)
                .map(layout -> ResponseEntity.ok(ApiResponse.success(layout)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND, "Station not found: " + stationId)));
    }
}
