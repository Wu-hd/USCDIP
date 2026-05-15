package com.uscdip.backend.dto;

import java.math.BigDecimal;

public record GisObjectPickResponse(
        GisObjectRecordResponse object,
        BigDecimal distanceMeters,
        BigDecimal toleranceMeters
) {
}
