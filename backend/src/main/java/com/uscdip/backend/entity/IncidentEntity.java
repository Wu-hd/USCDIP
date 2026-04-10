package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "incident")
public class IncidentEntity {

    @Id
    @Column(name = "incident_id", length = 64)
    private String incidentId;

    @Column(name = "segment_id", nullable = false, length = 64)
    private String segmentId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
