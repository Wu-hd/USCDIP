package com.uscdip.backend.controller;

import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.BoundarySpec;
import com.uscdip.backend.model.PlatformBoundary;
import com.uscdip.backend.service.BoundarySpecService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BoundaryController {

    private final BoundarySpecService boundarySpecService;

    public BoundaryController(BoundarySpecService boundarySpecService) {
        this.boundarySpecService = boundarySpecService;
    }

    @GetMapping("/menu-boundaries")
    public ApiResponse<BoundarySpec> getMenuBoundaries() {
        return ApiResponse.success(boundarySpecService.getBoundarySpec());
    }

    @GetMapping("/platforms")
    public ApiResponse<List<PlatformBoundary>> getPlatforms() {
        return ApiResponse.success(boundarySpecService.getPlatforms());
    }

    @GetMapping("/platforms/{platformCode}")
    public ResponseEntity<ApiResponse<PlatformBoundary>> getPlatform(@PathVariable String platformCode) {
        return boundarySpecService.findByPlatformCode(platformCode)
                .map(platform -> ResponseEntity.ok(ApiResponse.success(platform)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure("PLATFORM_NOT_FOUND", "Platform not found: " + platformCode, null)));
    }
}
