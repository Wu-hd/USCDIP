package com.uscdip.backend.dto;

public record AlertCaseProcessingSummary(
        int openedCaseCount,
        int dedupedCount,
        int suppressedCount,
        int escalatedCount
) {

    public static AlertCaseProcessingSummary empty() {
        return new AlertCaseProcessingSummary(0, 0, 0, 0);
    }
}
