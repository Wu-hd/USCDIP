package com.uscdip.backend.exception;

import com.uscdip.backend.model.ApiErrorCode;
import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final HttpStatus status;

    public BusinessException(ApiErrorCode errorCode, HttpStatus status) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.status = status;
    }

    public BusinessException(ApiErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
