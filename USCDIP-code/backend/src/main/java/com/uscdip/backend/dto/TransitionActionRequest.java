package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransitionActionRequest {

    @NotBlank
    private String action;

    private String feedbackType;
    private String feedbackReason;
}
