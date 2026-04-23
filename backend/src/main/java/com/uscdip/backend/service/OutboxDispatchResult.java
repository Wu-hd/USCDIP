package com.uscdip.backend.service;

public record OutboxDispatchResult(boolean success, String errorMessage) {

    public static OutboxDispatchResult ok() {
        return new OutboxDispatchResult(true, null);
    }

    public static OutboxDispatchResult failure(String errorMessage) {
        return new OutboxDispatchResult(false, errorMessage);
    }
}
