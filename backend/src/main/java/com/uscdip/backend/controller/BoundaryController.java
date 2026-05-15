package com.uscdip.backend.controller;

import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.BoundarySpec;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.PlatformBoundary;
import com.uscdip.backend.service.BoundarySpecService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Boundary", description = "A-01 平台边界与菜单契约查询")
public class BoundaryController {

    private final BoundarySpecService boundarySpecService;

    public BoundaryController(BoundarySpecService boundarySpecService) {
        this.boundarySpecService = boundarySpecService;
    }

    @GetMapping("/menu-boundaries")
    @Operation(summary = "查询统一菜单边界")
    public ApiResponse<BoundarySpec> getMenuBoundaries() {
        return ApiResponse.success(boundarySpecService.getBoundarySpec());
    }

    @GetMapping("/platforms")
    @Operation(summary = "查询平台列表")
    public ApiResponse<List<PlatformBoundary>> getPlatforms() {
        return ApiResponse.success(boundarySpecService.getPlatforms());
    }

    @GetMapping("/platforms/{platformCode}")
    @Operation(summary = "按平台编码查询边界")
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "平台不存在",
            content = @Content(schema = @Schema(implementation = com.uscdip.backend.model.ApiResponse.class))
    )
    public ResponseEntity<ApiResponse<PlatformBoundary>> getPlatform(
            @Parameter(description = "平台编码", required = true) @PathVariable String platformCode
    ) {
        return boundarySpecService.findByPlatformCode(platformCode)
                .map(platform -> ResponseEntity.ok(ApiResponse.success(platform)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(ErrorCode.PLATFORM_NOT_FOUND, "Platform not found: " + platformCode)));
    }
}
