package com.uscdip.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GisFieldSpecItem {

    private String fieldName;
    private String dataType;
    private boolean required;
    private String description;
    private String scope;
    private String sample;
}
