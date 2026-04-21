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
    OIDC_DISABLED("OIDC_DISABLED", "OIDC authentication is disabled"),
    OIDC_STATE_INVALID("OIDC_STATE_INVALID", "OIDC state is invalid"),
    OIDC_LOGIN_REQUIRED("OIDC_LOGIN_REQUIRED", "OIDC login is required"),
    OIDC_USER_SYNC_FAILED("OIDC_USER_SYNC_FAILED", "OIDC user synchronization failed"),
    OIDC_LOGOUT_FAILED("OIDC_LOGOUT_FAILED", "OIDC logout failed"),
    TOKEN_REFRESH_INVALID("TOKEN_REFRESH_INVALID", "Refresh token is invalid"),
    TOKEN_REFRESH_EXPIRED("TOKEN_REFRESH_EXPIRED", "Refresh token is expired"),
    TOKEN_REFRESH_REPLAY_DETECTED("TOKEN_REFRESH_REPLAY_DETECTED", "Refresh token replay detected"),
    TOKEN_REFRESH_REVOKED("TOKEN_REFRESH_REVOKED", "Refresh token is revoked"),
    TOKEN_ISSUE_FAILED("TOKEN_ISSUE_FAILED", "Token issue failed"),
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
