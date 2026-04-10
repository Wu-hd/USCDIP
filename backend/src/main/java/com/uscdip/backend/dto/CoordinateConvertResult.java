package com.uscdip.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CoordinateConvertResult {

    private String sourceSrid;
    private String targetSrid;
    private String sourceGeometry;
    private String convertedGeometry;
    private String conversionMode;
    private String message;
}
