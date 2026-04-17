package com.uscdip.backend.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StateTransitionRule {

    private String from;
    private String action;
    private String to;
    private String description;
}
