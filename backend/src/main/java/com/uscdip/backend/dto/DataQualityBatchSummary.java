package com.uscdip.backend.dto;

import java.util.Map;

public record DataQualityBatchSummary(
        Double avgDqScore,
        Double lowestDqScore,
        Double highestDqScore,
        Map<String, Long> levelBreakdown
) {
}
