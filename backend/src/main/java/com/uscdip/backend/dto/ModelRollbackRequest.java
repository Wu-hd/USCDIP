package com.uscdip.backend.dto;

public record ModelRollbackRequest(
        String targetVersionNo,
        String reason
) {
}
