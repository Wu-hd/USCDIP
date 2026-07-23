package com.uscdip.backend.dto;

import java.time.LocalDateTime;

public record WorkOrderResponse(
        String workOrderId,
        String incidentId,
        String segmentId,
        String nodeId,
        String workOrderType,
        String priority,
        String description,
        String assigneeUserId,
        String assignee,
        String status,
        LocalDateTime slaDueAt,
        String createdBy,
        String dispatchedBy,
        String acceptedBy,
        String completedBy,
        String closedBy,
        LocalDateTime dispatchedAt,
        LocalDateTime acceptedAt,
        LocalDateTime completedAt,
        LocalDateTime closedAt,
        String completionSummary,
        String closeReason,
        String writebackType,
        String writebackReason,
        LocalDateTime writebackAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long versionNo
) {
}
