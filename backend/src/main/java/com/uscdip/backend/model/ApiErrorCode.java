package com.uscdip.backend.model;

public enum ApiErrorCode {

    AUTHENTICATION_FAILED("AUTHENTICATION_FAILED", "Authentication failed or session expired"),
    FORBIDDEN("FORBIDDEN", "Permission denied"),
    DATA_SCOPE_EMPTY("DATA_SCOPE_EMPTY", "No data available in authorized scope"),
    IDEMPOTENT_CONFLICT("IDEMPOTENT_CONFLICT", "Idempotent conflict detected"),
    INVALID_PARAMETER("INVALID_PARAMETER", "Request parameter validation failed"),
    INVALID_REQUEST("INVALID_REQUEST", "Invalid request payload"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Requested resource was not found"),
    INVALID_STATE_TRANSITION("INVALID_STATE_TRANSITION", "Invalid state transition"),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String defaultMessage;

    ApiErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
