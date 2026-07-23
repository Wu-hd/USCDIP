package com.uscdip.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record WebSocketAckRequest(
        @NotBlank(message = "topic is required")
        String topic,

        @Min(value = 0, message = "lastAckSeq must be greater than or equal to 0")
        Long lastAckSeq
) {
}
