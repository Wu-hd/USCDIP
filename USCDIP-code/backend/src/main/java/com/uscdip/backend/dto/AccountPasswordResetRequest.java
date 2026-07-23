package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccountPasswordResetRequest(
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$",
                message = "must be 10-72 characters and include uppercase, lowercase, number, and special character"
        )
        String password
) {
}
