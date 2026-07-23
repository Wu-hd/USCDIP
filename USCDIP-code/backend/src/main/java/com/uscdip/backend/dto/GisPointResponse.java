package com.uscdip.backend.dto;

import java.math.BigDecimal;

public record GisPointResponse(
        BigDecimal x,
        BigDecimal y
) {
}
