package com.uscdip.backend.model;

public enum IdempotentRecordStatus {
    PROCESSING,
    SUCCESS,
    FAILED,
    DEAD
}
