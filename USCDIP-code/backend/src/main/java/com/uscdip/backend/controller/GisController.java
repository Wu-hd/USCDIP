package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.CoordinateConvertRequest;
import com.uscdip.backend.dto.CoordinateConvertResult;
import com.uscdip.backend.dto.DepthValidationRequest;
import com.uscdip.backend.dto.DepthValidationResult;
import com.uscdip.backend.dto.GisObjectPickRequest;
import com.uscdip.backend.dto.GisObjectPickResponse;
import com.uscdip.backend.dto.GisObjectRecordResponse;
import com.uscdip.backend.dto.GisFieldSpecItem;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.GisCoordinateService;
import com.uscdip.backend.service.GisSpatialQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gis")
@Tag(name = "GIS", description = "A-03 坐标与深度字段规范服务")
public class GisController {

    private final GisCoordinateService gisCoordinateService;
    private final GisSpatialQueryService gisSpatialQueryService;
    private final CurrentUserResolver currentUserResolver;

    public GisController(
            GisCoordinateService gisCoordinateService,
            GisSpatialQueryService gisSpatialQueryService,
            CurrentUserResolver currentUserResolver
    ) {
        this.gisCoordinateService = gisCoordinateService;
        this.gisSpatialQueryService = gisSpatialQueryService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/field-spec")
    @Operation(summary = "查询 GIS 字段规范")
    public ApiResponse<Map<String, Object>> getFieldSpec() {
        List<GisFieldSpecItem> fields = gisCoordinateService.getFieldSpec();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a03", "坐标与深度字段冻结");
        payload.put("fields", fields);
        return ApiResponse.success(payload);
    }

    @PostMapping("/convert")
    @Operation(summary = "统一坐标转换服务")
    public ApiResponse<CoordinateConvertResult> convert(@Valid @RequestBody CoordinateConvertRequest request) {
        return ApiResponse.success(gisCoordinateService.convert(request));
    }

    @PostMapping("/depth/validate")
    @Operation(summary = "埋深字段一致性校验")
    public ApiResponse<DepthValidationResult> validateDepth(@Valid @RequestBody DepthValidationRequest request) {
        return ApiResponse.success(gisCoordinateService.validateDepth(request));
    }

    @GetMapping("/objects/bbox")
    @AuthzGuard(menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "按 bbox 查询 GIS 对象")
    public ApiResponse<PageResponse<GisObjectRecordResponse>> queryByBbox(
            Authentication authentication,
            @RequestParam BigDecimal minX,
            @RequestParam BigDecimal minY,
            @RequestParam BigDecimal maxX,
            @RequestParam BigDecimal maxY,
            @RequestParam(defaultValue = "EPSG:4490") String authoritySrid,
            @RequestParam(defaultValue = "EPSG:3857") String displaySrid,
            @RequestParam(required = false) String objectType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(gisSpatialQueryService.queryByBbox(
                currentUserResolver.requireContext(authentication),
                minX,
                minY,
                maxX,
                maxY,
                authoritySrid,
                displaySrid,
                objectType,
                page,
                pageSize
        ));
    }

    @PostMapping("/objects/pick")
    @AuthzGuard(menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "按点位命中最近 GIS 对象")
    public ResponseEntity<ApiResponse<GisObjectPickResponse>> pickObject(
            Authentication authentication,
            @Valid @RequestBody GisObjectPickRequest request
    ) {
        return gisSpatialQueryService.pickObject(currentUserResolver.requireContext(authentication), request)
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND, "No GIS object matched the given point")));
    }

    @GetMapping("/objects/{objectType}/{objectId}")
    @AuthzGuard(menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询单对象 GIS 信息与对象链主键")
    public ResponseEntity<ApiResponse<GisObjectRecordResponse>> getObjectDetail(
            Authentication authentication,
            @PathVariable String objectType,
            @PathVariable String objectId,
            @RequestParam(defaultValue = "EPSG:3857") String displaySrid
    ) {
        return gisSpatialQueryService.getObjectDetail(
                        currentUserResolver.requireContext(authentication),
                        objectType,
                        objectId,
                        displaySrid
                )
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND, "GIS object not found: " + objectType + ":" + objectId)));
    }
}
