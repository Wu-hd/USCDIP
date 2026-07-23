package com.uscdip.backend.service;

public record IdempotentConsumerResult(
        String idempotentKey,
        String status,
        String resultRefId,
        boolean duplicate,
        boolean dead
) {
}
