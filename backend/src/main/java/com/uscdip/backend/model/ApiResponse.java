package com.uscdip.backend.model;

import com.uscdip.backend.support.TraceIdContext;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, ApiError error, String traceId, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, TraceIdContext.currentOrGenerate(), Instant.now());
    }

    public static <T> ApiResponse<T> failure(ErrorCode code) {
        return failure(code, code.defaultMessage());
    }

    public static <T> ApiResponse<T> failure(ErrorCode code, String message) {
        return new ApiResponse<>(
                false,
                null,
                new ApiError(code.code(), message),
                TraceIdContext.currentOrGenerate(),
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> failure(ErrorCode code, String message, T data) {
        return new ApiResponse<>(
                false,
                data,
                new ApiError(code.code(), message),
                TraceIdContext.currentOrGenerate(),
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> failure(String code, String message, String traceId) {
        String resolvedTraceId = (traceId == null || traceId.isBlank()) ? TraceIdContext.currentOrGenerate() : traceId;
        return new ApiResponse<>(false, null, new ApiError(code, message), resolvedTraceId, Instant.now());
    }

    public record ApiError(String code, String message) {
    }
}
