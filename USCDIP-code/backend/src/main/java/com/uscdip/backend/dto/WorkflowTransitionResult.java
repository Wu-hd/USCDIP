package com.uscdip.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class WorkflowTransitionResult {

    private String machineType;
    private String entityId;
    private String previousStatus;
    private String action;
    private String currentStatus;
    private LocalDateTime updatedAt;
    private List<String> allowedActions;
}
