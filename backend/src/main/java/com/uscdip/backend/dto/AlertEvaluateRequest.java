package com.uscdip.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "B-17 告警规则手动评估请求")
public record AlertEvaluateRequest(
        @Schema(description = "指定源记录 ID 列表，与 sourceBatchId 二选一")
        List<String> sourceRecordIds,
        @Schema(description = "指定源批次 ID，与 sourceRecordIds 二选一", example = "INGB-SEED-B16-STD-001")
        String sourceBatchId,
        @Schema(description = "指定规则编码列表；为空时评估全部启用规则")
        List<String> ruleCodes
) {
}
