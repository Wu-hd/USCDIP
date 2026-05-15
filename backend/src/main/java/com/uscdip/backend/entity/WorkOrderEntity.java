package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "work_order",
        indexes = {
                @Index(name = "idx_work_order_incident_status", columnList = "incident_id,status"),
                @Index(name = "idx_work_order_assignee_status", columnList = "assignee,status"),
                @Index(name = "idx_work_order_updated", columnList = "updated_at")
        }
)
public class WorkOrderEntity {

    @Id
    @Column(name = "work_order_id", length = 64)
    private String workOrderId;

    @Column(name = "incident_id", nullable = false, length = 64)
    private String incidentId;

    @Column(name = "segment_id", nullable = false, length = 64)
    private String segmentId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "work_order_type", length = 64)
    private String workOrderType;

    @Column(name = "priority", length = 32)
    private String priority;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "assignee_user_id", length = 64)
    private String assigneeUserId;

    @Column(name = "assignee", length = 64)
    private String assignee;

    @Column(name = "feedback_type", length = 64)
    private String feedbackType;

    @Column(name = "feedback_reason", length = 512)
    private String feedbackReason;

    @Column(name = "last_action", length = 64)
    private String lastAction;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "dispatched_by", length = 64)
    private String dispatchedBy;

    @Column(name = "accepted_by", length = 64)
    private String acceptedBy;

    @Column(name = "completed_by", length = 64)
    private String completedBy;

    @Column(name = "closed_by", length = 64)
    private String closedBy;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "completion_summary", length = 1000)
    private String completionSummary;

    @Column(name = "close_reason", length = 1000)
    private String closeReason;

    @Column(name = "writeback_type", length = 64)
    private String writebackType;

    @Column(name = "writeback_reason", length = 1000)
    private String writebackReason;

    @Column(name = "writeback_at")
    private LocalDateTime writebackAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "version_no")
    private Long versionNo;
}
