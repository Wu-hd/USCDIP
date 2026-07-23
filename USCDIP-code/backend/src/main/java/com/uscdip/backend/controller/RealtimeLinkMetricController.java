package com.uscdip.backend.controller;

import com.uscdip.backend.dto.RealtimeFieldSpecItem;
import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.service.RealtimeLinkMetricService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeLinkMetricController {

    private final RealtimeLinkMetricService realtimeLinkMetricService;

    public RealtimeLinkMetricController(RealtimeLinkMetricService realtimeLinkMetricService) {
        this.realtimeLinkMetricService = realtimeLinkMetricService;
    }

    @GetMapping("/field-spec")
    public ApiResponse<Map<String, Object>> getFieldSpec() {
        List<RealtimeFieldSpecItem> fields = realtimeLinkMetricService.getFieldSpec();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a05", "实时链路指标口径表");
        payload.put("fields", fields);
        return ApiResponse.success(payload);
    }

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> listMetrics(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(realtimeLinkMetricService.listRecent(limit));
    }

    @GetMapping("/traces/{traceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTraceTimeline(@PathVariable String traceId) {
        Map<String, Object> payload = realtimeLinkMetricService.getTraceTimeline(traceId);
        int total = (int) payload.get("total");
        if (total == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("TRACE_NOT_FOUND", "Trace not found: " + traceId, null));
        }
        return ResponseEntity.ok(ApiResponse.success(payload));
    }
}
