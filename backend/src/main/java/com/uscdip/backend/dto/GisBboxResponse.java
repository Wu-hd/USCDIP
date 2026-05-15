package com.uscdip.backend.dto;

import java.math.BigDecimal;

public record GisBboxResponse(
        BigDecimal minX,
        BigDecimal minY,
        BigDecimal maxX,
        BigDecimal maxY
) {
}
