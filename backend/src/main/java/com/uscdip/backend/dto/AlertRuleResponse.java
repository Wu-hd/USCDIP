package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record AlertRuleResponse(
        String ruleId,
        String ruleCode,
        String ruleName,
        String ruleType,
        String metricCode,
        String operator,
        Double thresholdLow,
        Double thresholdHigh,
        String logicType,
        Double baseConfidence,
        String severity,
        boolean enabled,
        String expressionJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
