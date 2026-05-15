package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "trace_link_event",
        indexes = {
                @Index(name = "idx_trace_link_trace_time", columnList = "trace_id,event_time"),
                @Index(name = "idx_trace_link_stage_time", columnList = "stage,event_time"),
                @Index(name = "idx_trace_link_object", columnList = "object_type,object_id")
        }
)
public class TraceLinkEventEntity {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "span_id", length = 64)
    private String spanId;

    @Column(name = "parent_span_id", length = 64)
    private String parentSpanId;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "source_module", nullable = false, length = 64)
    private String sourceModule;

    @Column(name = "object_type", length = 64)
    private String objectType;

    @Column(name = "object_id", length = 128)
    private String objectId;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "recv_time")
    private LocalDateTime recvTime;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "route_path", length = 512)
    private String routePath;

    @Column(name = "message_id", length = 128)
    private String messageId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "topic", length = 256)
    private String topic;

    @Column(name = "detail", length = 2000)
    private String detail;
}
