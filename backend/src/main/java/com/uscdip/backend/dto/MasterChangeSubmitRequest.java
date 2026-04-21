package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record MasterChangeSubmitRequest(
        @NotBlank String objectType,
        @NotBlank String objectId,
        @NotNull Long baseVersionNo,
        @NotBlank String reason,
        @NotNull Map<String, Object> payload
) {
}
