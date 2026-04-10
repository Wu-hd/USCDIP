package com.uscdip.backend.model;

public record ApiResponse<T>(boolean success, T data, ApiError error, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message, String traceId) {
        return new ApiResponse<>(false, null, new ApiError(code, message), traceId);
    }

    public record ApiError(String code, String message) {
    }
}
