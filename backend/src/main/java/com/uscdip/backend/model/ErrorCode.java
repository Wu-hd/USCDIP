package com.uscdip.backend.model;

public enum ErrorCode {
    INVALID_PARAMETER("INVALID_PARAMETER", "Request parameter validation failed"),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication required"),
    FORBIDDEN("FORBIDDEN", "Permission denied"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Requested resource was not found"),
    DATA_SCOPE_EMPTY("DATA_SCOPE_EMPTY", "Data scope is empty"),
    IDEMPOTENT_CONFLICT("IDEMPOTENT_CONFLICT", "Duplicate request conflict"),
    PLATFORM_NOT_FOUND("PLATFORM_NOT_FOUND", "Platform not found"),
    SEGMENT_NOT_FOUND("SEGMENT_NOT_FOUND", "Segment not found"),
    NODE_NOT_FOUND("NODE_NOT_FOUND", "Node not found"),
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found"),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
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
