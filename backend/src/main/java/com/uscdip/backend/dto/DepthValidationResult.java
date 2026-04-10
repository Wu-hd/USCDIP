package com.uscdip.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DepthValidationResult {

    private boolean valid;
    private BigDecimal expectedBuryDepth;
    private BigDecimal actualBuryDepth;
    private BigDecimal delta;
    private BigDecimal tolerance;
    private String message;
}
