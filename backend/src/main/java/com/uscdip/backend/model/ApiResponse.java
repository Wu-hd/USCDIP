package com.uscdip.backend.model;

public record ApiResponse<T>(boolean success, T data, ApiError error, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, TraceIdContext.currentTraceId());
    }

    public static <T> ApiResponse<T> failure(String code, String message, String traceId) {
        String finalTraceId = traceId == null ? TraceIdContext.currentTraceId() : traceId;
        return new ApiResponse<>(false, null, new ApiError(code, message), finalTraceId);
    }

    public record ApiError(String code, String message) {
    }
}
