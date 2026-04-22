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
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", "User account is disabled"),
    EMERGENCY_LOGIN_FAILED("EMERGENCY_LOGIN_FAILED", "Emergency account login failed"),
    EMERGENCY_ACCOUNT_NOT_AVAILABLE("EMERGENCY_ACCOUNT_NOT_AVAILABLE", "Emergency account is not available"),
    ENTRY_PERMISSION_DENIED("ENTRY_PERMISSION_DENIED", "Entry permission denied"),
    MENU_PERMISSION_DENIED("MENU_PERMISSION_DENIED", "Menu permission denied"),
    DATA_SCOPE_DENIED("DATA_SCOPE_DENIED", "Data scope denied"),
    TOPIC_SCOPE_DENIED("TOPIC_SCOPE_DENIED", "Topic scope denied"),
    SUBSCRIPTION_NOT_ALLOWED("SUBSCRIPTION_NOT_ALLOWED", "Subscription is not allowed"),
    GATEWAY_BLOCKED("GATEWAY_BLOCKED", "Request was blocked by gateway policy"),
    RATE_LIMITED("RATE_LIMITED", "Request exceeded gateway rate limit"),
    UNSUPPORTED_CONTENT_TYPE("UNSUPPORTED_CONTENT_TYPE", "Request content type is not supported"),
    REQUEST_BODY_TOO_LARGE("REQUEST_BODY_TOO_LARGE", "Request body is too large"),
    TOKEN_REFRESH_INVALID("TOKEN_REFRESH_INVALID", "Refresh token is invalid"),
    TOKEN_REFRESH_EXPIRED("TOKEN_REFRESH_EXPIRED", "Refresh token is expired"),
    TOKEN_REFRESH_REPLAY_DETECTED("TOKEN_REFRESH_REPLAY_DETECTED", "Refresh token replay detected"),
    TOKEN_REFRESH_REVOKED("TOKEN_REFRESH_REVOKED", "Refresh token is revoked"),
    TOKEN_ISSUE_FAILED("TOKEN_ISSUE_FAILED", "Token issue failed"),
    MASTER_DATA_VERSION_CONFLICT("MASTER_DATA_VERSION_CONFLICT", "Master data version conflict"),
    MASTER_CHANGE_PENDING("MASTER_CHANGE_PENDING", "Master data change is pending"),
    MASTER_CHANGE_NOT_FOUND("MASTER_CHANGE_NOT_FOUND", "Master data change was not found"),
    MASTER_CHANGE_INVALID_STATE("MASTER_CHANGE_INVALID_STATE", "Master data change state is invalid"),
    MASTER_CHANGE_APPROVAL_REQUIRED("MASTER_CHANGE_APPROVAL_REQUIRED", "Master data change approval is required"),
    DEVICE_NOT_FOUND("DEVICE_NOT_FOUND", "Device was not found"),
    DEVICE_RELATION_INVALID("DEVICE_RELATION_INVALID", "Device relation is invalid"),
    DEVICE_HEARTBEAT_INVALID("DEVICE_HEARTBEAT_INVALID", "Device heartbeat payload is invalid"),
    INGEST_PROTOCOL_UNSUPPORTED("INGEST_PROTOCOL_UNSUPPORTED", "Ingest protocol is not supported"),
    INGEST_DEVICE_NOT_FOUND("INGEST_DEVICE_NOT_FOUND", "Ingest device was not found"),
    INGEST_PAYLOAD_INVALID("INGEST_PAYLOAD_INVALID", "Ingest payload is invalid"),
    INGEST_BATCH_NOT_FOUND("INGEST_BATCH_NOT_FOUND", "Ingest batch was not found"),
    TSDB_WRITE_FAILED("TSDB_WRITE_FAILED", "TSDB write failed"),
    TSDB_WRITE_LOG_NOT_FOUND("TSDB_WRITE_LOG_NOT_FOUND", "TSDB write log was not found"),
    BACKFILL_PAYLOAD_INVALID("BACKFILL_PAYLOAD_INVALID", "Backfill payload is invalid"),
    BACKFILL_DUPLICATE("BACKFILL_DUPLICATE", "Backfill payload duplicates an existing sample"),
    DQ_SCORE_NOT_FOUND("DQ_SCORE_NOT_FOUND", "Data quality score was not found"),
    DQ_QUERY_INVALID("DQ_QUERY_INVALID", "Data quality query is invalid"),
    DQ_PROFILE_INVALID("DQ_PROFILE_INVALID", "Data quality profile is invalid"),
    CALIBRATION_PROFILE_NOT_FOUND("CALIBRATION_PROFILE_NOT_FOUND", "Calibration profile was not found"),
    CALIBRATION_PROFILE_CONFLICT("CALIBRATION_PROFILE_CONFLICT", "Calibration profile is invalid or conflicts with existing data"),
    CALIBRATION_DRIFT_EVALUATION_INVALID("CALIBRATION_DRIFT_EVALUATION_INVALID", "Calibration drift evaluation payload is invalid"),
    CALIBRATION_CORRECTION_PREVIEW_INVALID("CALIBRATION_CORRECTION_PREVIEW_INVALID", "Calibration correction preview request is invalid"),
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
