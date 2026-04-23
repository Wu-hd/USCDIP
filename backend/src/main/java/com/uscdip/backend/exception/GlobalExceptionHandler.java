package com.uscdip.backend.exception;

import com.uscdip.backend.model.ApiResponse;
import com.uscdip.backend.model.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(resolveValidationErrorCode(request), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(resolveValidationErrorCode(request), ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(resolveValidationErrorCode(request), "Request body format is invalid"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(resolveValidationErrorCode(request), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(resolveValidationErrorCode(request), ex.getMessage()));
    }

    @ExceptionHandler(AuthFlowException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthFlowException(AuthFlowException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.failure(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ErrorCode.MASTER_DATA_VERSION_CONFLICT, ErrorCode.MASTER_DATA_VERSION_CONFLICT.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    private String toFieldMessage(FieldError fieldError) {
        if (fieldError.getDefaultMessage() == null || fieldError.getDefaultMessage().isBlank()) {
            return fieldError.getField() + " is invalid";
        }
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ErrorCode resolveValidationErrorCode(HttpServletRequest request) {
        if (request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/dq")) {
            return ErrorCode.DQ_QUERY_INVALID;
        }
        if (request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/alerts")) {
            return ErrorCode.ALERT_EVALUATION_INVALID;
        }
        if (request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/models")) {
            return ErrorCode.MODEL_GATEWAY_INVALID;
        }
        if (request != null && request.getRequestURI() != null && request.getRequestURI().contains("/api/calibration/devices/")
                && request.getRequestURI().contains("/drift-checks")) {
            return ErrorCode.CALIBRATION_DRIFT_EVALUATION_INVALID;
        }
        if (request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/calibration/metrics/")) {
            return ErrorCode.CALIBRATION_CORRECTION_PREVIEW_INVALID;
        }
        if (request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/ingest/backfill")) {
            return ErrorCode.BACKFILL_PAYLOAD_INVALID;
        }
        if (request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/ingest")) {
            return ErrorCode.INGEST_PAYLOAD_INVALID;
        }
        return ErrorCode.INVALID_PARAMETER;
    }
}
