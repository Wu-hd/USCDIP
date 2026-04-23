package com.uscdip.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.entity.OutboxEventEntity;
import com.uscdip.backend.entity.WorkOrderEntity;
import com.uscdip.backend.model.OutboxEventStatus;
import com.uscdip.backend.repository.OutboxEventRepository;
import com.uscdip.backend.support.TraceIdContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    public static final String AGGREGATE_TYPE_INCIDENT = "INCIDENT";
    public static final String AGGREGATE_TYPE_WORK_ORDER = "WORK_ORDER";
    public static final String EVENT_INCIDENT_OPENED = "INCIDENT_OPENED";
    public static final String EVENT_INCIDENT_UPDATED = "INCIDENT_UPDATED";
    public static final String EVENT_INCIDENT_RESOLVED = "INCIDENT_RESOLVED";
    public static final String EVENT_INCIDENT_CONFIRMED = "INCIDENT_CONFIRMED";
    public static final String EVENT_WORK_ORDER_CREATED = "WORK_ORDER_CREATED";
    public static final String EVENT_WORK_ORDER_DISPATCHED = "WORK_ORDER_DISPATCHED";
    public static final String EVENT_WORK_ORDER_ACCEPTED = "WORK_ORDER_ACCEPTED";
    public static final String EVENT_WORK_ORDER_TRANSFERRED = "WORK_ORDER_TRANSFERRED";
    public static final String EVENT_WORK_ORDER_COMPLETED = "WORK_ORDER_COMPLETED";
    public static final String EVENT_WORK_ORDER_CLOSED = "WORK_ORDER_CLOSED";
    public static final String EVENT_WORK_ORDER_WRITEBACK_RECORDED = "WORK_ORDER_WRITEBACK_RECORDED";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OutboxEventEntity publishIncidentChanged(IncidentEntity incident, boolean newlyOpened, LocalDateTime occurredAt) {
        return publishIncidentEvent(
                incident,
                newlyOpened ? EVENT_INCIDENT_OPENED : EVENT_INCIDENT_UPDATED,
                occurredAt
        );
    }

    @Transactional
    public OutboxEventEntity publishIncidentResolved(IncidentEntity incident, LocalDateTime occurredAt) {
        return publishIncidentEvent(incident, EVENT_INCIDENT_RESOLVED, occurredAt);
    }

    @Transactional
    public OutboxEventEntity publishIncidentConfirmed(IncidentEntity incident, LocalDateTime occurredAt) {
        return publishIncidentEvent(incident, EVENT_INCIDENT_CONFIRMED, occurredAt);
    }

    @Transactional
    public OutboxEventEntity publishWorkOrderEvent(WorkOrderEntity workOrder, String eventType, LocalDateTime occurredAt) {
        if (workOrder == null || isBlank(workOrder.getWorkOrderId()) || isBlank(eventType)) {
            return null;
        }
        LocalDateTime now = occurredAt == null ? LocalDateTime.now() : occurredAt;
        String traceId = TraceIdContext.currentOrGenerate();
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId("OBE-" + UUID.randomUUID());
        event.setAggregateType(AGGREGATE_TYPE_WORK_ORDER);
        event.setAggregateId(workOrder.getWorkOrderId());
        event.setEventType(eventType);
        event.setPayload(toWorkOrderPayload(workOrder, now, traceId));
        event.setTraceId(traceId);
        event.setStatus(OutboxEventStatus.NEW.name());
        event.setRetryCount(0);
        event.setNextRetryTime(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return outboxEventRepository.save(event);
    }

    private OutboxEventEntity publishIncidentEvent(IncidentEntity incident, String eventType, LocalDateTime occurredAt) {
        if (incident == null || isBlank(incident.getIncidentId())) {
            return null;
        }
        LocalDateTime now = occurredAt == null ? LocalDateTime.now() : occurredAt;
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId("OBE-" + UUID.randomUUID());
        event.setAggregateType(AGGREGATE_TYPE_INCIDENT);
        event.setAggregateId(incident.getIncidentId());
        event.setEventType(eventType);
        event.setPayload(toPayload(incident, now));
        event.setTraceId(incident.getTraceId());
        event.setStatus(OutboxEventStatus.NEW.name());
        event.setRetryCount(0);
        event.setNextRetryTime(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return outboxEventRepository.save(event);
    }

    private String toPayload(IncidentEntity incident, LocalDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incidentId", incident.getIncidentId());
        payload.put("status", incident.getStatus());
        payload.put("severity", incident.getSeverity());
        payload.put("versionNo", incident.getVersionNo());
        payload.put("sourceCaseId", incident.getSourceCaseId());
        payload.put("sourceAlertId", incident.getSourceAlertId());
        payload.put("traceId", incident.getTraceId());
        payload.put("occurredAt", occurredAt);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload for incident " + incident.getIncidentId(), ex);
        }
    }

    private String toWorkOrderPayload(WorkOrderEntity workOrder, LocalDateTime occurredAt, String traceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workOrderId", workOrder.getWorkOrderId());
        payload.put("incidentId", workOrder.getIncidentId());
        payload.put("status", workOrder.getStatus());
        payload.put("assigneeUserId", workOrder.getAssigneeUserId());
        payload.put("assignee", workOrder.getAssignee());
        payload.put("versionNo", workOrder.getVersionNo());
        payload.put("writebackType", workOrder.getWritebackType());
        payload.put("traceId", traceId);
        payload.put("occurredAt", occurredAt);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload for work order " + workOrder.getWorkOrderId(), ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
