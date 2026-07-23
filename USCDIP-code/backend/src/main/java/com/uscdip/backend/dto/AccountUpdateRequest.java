package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record AccountUpdateRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 64) String primaryRegionId,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED", message = "must be ACTIVE or DISABLED") String status,
        @NotEmpty List<@NotBlank String> roleCodes,
        Map<String, List<String>> dataScopes
) {
}
