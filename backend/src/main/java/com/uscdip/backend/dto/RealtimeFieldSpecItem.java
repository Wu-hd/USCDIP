package com.uscdip.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RealtimeFieldSpecItem {

    private String field;
    private String type;
    private boolean required;
    private String stage;
    private String description;
    private String sample;
}
