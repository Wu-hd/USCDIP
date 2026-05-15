package com.uscdip.backend.model;

public enum OutboxEventStatus {
    NEW,
    SENDING,
    SENT,
    FAILED,
    DEAD
}
