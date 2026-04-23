package com.uscdip.backend.dto;

import java.util.List;

public record AlertEvaluateResponse(
        int sourceMetricCount,
        int evaluatedRuleCount,
        int generatedAlertCount,
        List<AlertRecordResponse> alertRecords
) {
}
