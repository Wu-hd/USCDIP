package com.uscdip.backend.controller;

import com.uscdip.backend.dto.CoordinateConvertRequest;
import com.uscdip.backend.dto.CoordinateConvertResult;
import com.uscdip.backend.dto.DepthValidationRequest;
import com.uscdip.backend.dto.DepthValidationResult;
import com.uscdip.backend.dto.GisFieldSpecItem;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.GisCoordinateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gis")
@Tag(name = "GIS", description = "A-03 坐标与深度字段规范服务")
public class GisController {

    private final GisCoordinateService gisCoordinateService;

    public GisController(GisCoordinateService gisCoordinateService) {
        this.gisCoordinateService = gisCoordinateService;
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
}
