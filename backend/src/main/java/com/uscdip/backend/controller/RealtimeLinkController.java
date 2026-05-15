package com.uscdip.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.RealtimeLinkSpecService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime-link")
@Validated
@Tag(name = "Realtime Link", description = "A-05 实时链路指标口径表")
public class RealtimeLinkController {

    private final RealtimeLinkSpecService realtimeLinkSpecService;

    public RealtimeLinkController(RealtimeLinkSpecService realtimeLinkSpecService) {
        this.realtimeLinkSpecService = realtimeLinkSpecService;
    }

    @GetMapping("/spec")
    @Operation(summary = "查询实时链路指标口径表")
    public ApiResponse<JsonNode> getRealtimeLinkSpec() {
        return ApiResponse.success(realtimeLinkSpecService.getSpec());
    }
}
