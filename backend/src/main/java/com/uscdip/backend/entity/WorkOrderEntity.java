package com.uscdip.backend.entity;

import com.uscdip.backend.model.WorkOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
@Table(name = "work_order")
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

    @Column(name = "assignee", length = 64)
    private String assignee;

    @Column(name = "feedback_type", length = 64)
    private String feedbackType;

    @Column(name = "feedback_reason", length = 512)
    private String feedbackReason;

    @Column(name = "last_action", length = 64)
    private String lastAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WorkOrderStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
