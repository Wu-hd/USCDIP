package com.uscdip.backend.dto;

import java.util.List;

public record AlertEvaluateResponse(
        int sourceMetricCount,
        int evaluatedRuleCount,
        int generatedAlertCount,
        int openedCaseCount,
        int dedupedCount,
        int suppressedCount,
        int escalatedCount,
        List<AlertRecordResponse> alertRecords
) {
}
