package com.uscdip.backend.controller;

import com.uscdip.backend.annotation.AuthzGuard;
import com.uscdip.backend.dto.DataQualityScoreResponse;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.service.CurrentUserResolver;
import com.uscdip.backend.service.DataQualityScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Validated
@RestController
@RequestMapping("/api/dq")
@Tag(name = "DataQuality", description = "B-14 数据质量评分服务")
public class DataQualityController {

    private final DataQualityScoringService dataQualityScoringService;
    private final CurrentUserResolver currentUserResolver;

    public DataQualityController(DataQualityScoringService dataQualityScoringService, CurrentUserResolver currentUserResolver) {
        this.dataQualityScoringService = dataQualityScoringService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/scores")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "分页查询数据质量评分结果")
    public ApiResponse<PageResponse<DataQualityScoreResponse>> getScores(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String metricCode,
            @RequestParam(required = false) String dqLevel,
            @RequestParam(required = false) Double minScore,
            @RequestParam(required = false) Double maxScore,
            @RequestParam(required = false) String sourceBatchId,
            @RequestParam(required = false) Boolean isBackfill,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        return ApiResponse.success(dataQualityScoringService.listScores(
                currentUserResolver.requireContext(authentication),
                page,
                pageSize,
                deviceId,
                metricCode,
                dqLevel,
                minScore,
                maxScore,
                sourceBatchId,
                isBackfill,
                startTime,
                endTime
        ));
    }

    @GetMapping("/scores/{sourceRecordId}")
    @AuthzGuard(entryPermission = "ENTRY:MGMT", menuPermission = "MENU:ASSET:READ")
    @Operation(summary = "查询单条数据质量评分结果")
    public ApiResponse<DataQualityScoreResponse> getScoreDetail(
            Authentication authentication,
            @PathVariable String sourceRecordId
    ) {
        return ApiResponse.success(dataQualityScoringService.getScoreDetail(
                currentUserResolver.requireContext(authentication),
                sourceRecordId
        ));
    }
}
