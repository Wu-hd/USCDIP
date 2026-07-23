package com.uscdip.backend.service;

import com.uscdip.backend.dto.TraceLinkEventResponse;
import com.uscdip.backend.entity.TraceLinkEventEntity;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.TraceLinkEventRepository;
import com.uscdip.backend.support.TraceIdContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TraceLinkService {

    public static final String STAGE_HTTP = "HTTP";
    public static final String STAGE_OUTBOX = "OUTBOX";
    public static final String STAGE_NOTIFICATION = "NOTIFICATION";
    public static final String STAGE_WEBSOCKET = "WEBSOCKET";

    public static final String SOURCE_HTTP = "http";
    public static final String SOURCE_OUTBOX = "outbox";
    public static final String SOURCE_NOTIFICATION = "notification";
    public static final String SOURCE_WEBSOCKET = "websocket";

    private static final int MAX_DETAIL_LENGTH = 2000;

    private final TraceLinkEventRepository traceLinkEventRepository;

    public TraceLinkService(TraceLinkEventRepository traceLinkEventRepository) {
        this.traceLinkEventRepository = traceLinkEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TraceLinkEventResponse record(TraceLinkCommand command) {
        LocalDateTime now = command.eventTime() == null ? LocalDateTime.now() : command.eventTime();
        TraceLinkEventEntity entity = new TraceLinkEventEntity();
        entity.setEventId(firstText(command.eventId(), "TLE-" + UUID.randomUUID()));
        entity.setTraceId(firstText(command.traceId(), TraceIdContext.currentOrGenerate()));
        entity.setSpanId(blankToNull(command.spanId()));
        entity.setParentSpanId(blankToNull(command.parentSpanId()));
        entity.setStage(required(command.stage()));
        entity.setEventType(required(command.eventType()));
        entity.setSourceModule(required(command.sourceModule()));
        entity.setObjectType(blankToNull(command.objectType()));
        entity.setObjectId(blankToNull(command.objectId()));
        entity.setStatus(blankToNull(command.status()));
        entity.setLatencyMs(command.latencyMs());
        entity.setEventTime(now);
        entity.setRecvTime(command.recvTime());
        entity.setActorUserId(blankToNull(command.actorUserId()));
        entity.setClientIp(blankToNull(command.clientIp()));
        entity.setRoutePath(blankToNull(command.routePath()));
        entity.setMessageId(blankToNull(command.messageId()));
        entity.setSessionId(blankToNull(command.sessionId()));
        entity.setTopic(blankToNull(command.topic()));
        entity.setDetail(abbreviate(command.detail()));
        return toResponse(traceLinkEventRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PageResponse<TraceLinkEventResponse> listTraceEvents(
            int page,
            int pageSize,
            String traceId,
            String stage,
            String sourceModule,
            String objectType,
            String objectId,
            String status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        List<TraceLinkEventResponse> visible = traceLinkEventRepository.findAllByOrderByEventTimeDescEventIdDesc().stream()
                .filter(event -> matches(event.getTraceId(), traceId))
                .filter(event -> matches(event.getStage(), stage))
                .filter(event -> matches(event.getSourceModule(), sourceModule))
                .filter(event -> matches(event.getObjectType(), objectType))
                .filter(event -> matches(event.getObjectId(), objectId))
                .filter(event -> matches(event.getStatus(), status))
                .filter(event -> from == null || !event.getEventTime().isBefore(from))
                .filter(event -> to == null || !event.getEventTime().isAfter(to))
                .map(this::toResponse)
                .toList();
        return paginate(visible, page, pageSize);
    }

    @Transactional(readOnly = true)
    public List<TraceLinkEventResponse> getTrace(String traceId) {
        return traceLinkEventRepository.findByTraceIdOrderByEventTimeAscEventIdAsc(traceId).stream()
                .map(this::toResponse)
                .toList();
    }

    private PageResponse<TraceLinkEventResponse> paginate(List<TraceLinkEventResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private TraceLinkEventResponse toResponse(TraceLinkEventEntity entity) {
        return new TraceLinkEventResponse(
                entity.getEventId(),
                entity.getTraceId(),
                entity.getSpanId(),
                entity.getParentSpanId(),
                entity.getStage(),
                entity.getEventType(),
                entity.getSourceModule(),
                entity.getObjectType(),
                entity.getObjectId(),
                entity.getStatus(),
                entity.getLatencyMs(),
                entity.getEventTime(),
                entity.getRecvTime(),
                entity.getActorUserId(),
                entity.getClientIp(),
                entity.getRoutePath(),
                entity.getMessageId(),
                entity.getSessionId(),
                entity.getTopic(),
                entity.getDetail()
        );
    }

    private boolean matches(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return actual != null && actual.trim().equalsIgnoreCase(expected.trim());
    }

    private String required(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? "UNKNOWN" : normalized.toUpperCase(Locale.ROOT);
    }

    private String abbreviate(String value) {
        String normalized = blankToNull(value);
        if (normalized == null || normalized.length() <= MAX_DETAIL_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_DETAIL_LENGTH);
    }

    private String firstText(String preferred, String fallback) {
        String normalized = blankToNull(preferred);
        return normalized == null ? fallback : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record TraceLinkCommand(
            String eventId,
            String traceId,
            String spanId,
            String parentSpanId,
            String stage,
            String eventType,
            String sourceModule,
            String objectType,
            String objectId,
            String status,
            Long latencyMs,
            LocalDateTime eventTime,
            LocalDateTime recvTime,
            String actorUserId,
            String clientIp,
            String routePath,
            String messageId,
            String sessionId,
            String topic,
            String detail
    ) {
    }
}
