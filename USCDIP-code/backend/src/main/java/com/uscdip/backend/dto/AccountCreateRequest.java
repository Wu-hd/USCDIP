package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record AccountCreateRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,63}", message = "must start with a letter and contain only letters, numbers, dot, underscore, or hyphen")
        String username,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 64) String primaryRegionId,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$",
                message = "must be 10-72 characters and include uppercase, lowercase, number, and special character"
        )
        String password,
        @NotEmpty List<@NotBlank String> roleCodes,
        Map<String, List<String>> dataScopes
) {
}
