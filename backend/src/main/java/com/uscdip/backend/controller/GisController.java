package com.uscdip.backend.controller;

import com.uscdip.backend.dto.CoordinateConvertRequest;
import com.uscdip.backend.dto.CoordinateConvertResult;
import com.uscdip.backend.dto.DepthValidationRequest;
import com.uscdip.backend.dto.DepthValidationResult;
import com.uscdip.backend.dto.GisFieldSpecItem;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.GisCoordinateService;
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
public class GisController {

    private final GisCoordinateService gisCoordinateService;

    public GisController(GisCoordinateService gisCoordinateService) {
        this.gisCoordinateService = gisCoordinateService;
    }

    @GetMapping("/field-spec")
    public ApiResponse<Map<String, Object>> getFieldSpec() {
        List<GisFieldSpecItem> fields = gisCoordinateService.getFieldSpec();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a03", "坐标与深度字段冻结");
        payload.put("fields", fields);
        return ApiResponse.success(payload);
    }

    @PostMapping("/convert")
    public ApiResponse<CoordinateConvertResult> convert(@RequestBody CoordinateConvertRequest request) {
        return ApiResponse.success(gisCoordinateService.convert(request));
    }

    @PostMapping("/depth/validate")
    public ApiResponse<DepthValidationResult> validateDepth(@RequestBody DepthValidationRequest request) {
        return ApiResponse.success(gisCoordinateService.validateDepth(request));
    }
}
