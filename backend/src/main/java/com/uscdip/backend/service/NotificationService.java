package com.uscdip.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.NotificationConsumeResponse;
import com.uscdip.backend.dto.NotificationDeliveryResponse;
import com.uscdip.backend.dto.NotificationResponse;
import com.uscdip.backend.entity.DeadLetterEntity;
import com.uscdip.backend.entity.NotificationDeliveryEntity;
import com.uscdip.backend.entity.NotificationMessageEntity;
import com.uscdip.backend.entity.OutboxEventEntity;
import com.uscdip.backend.entity.WorkOrderEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.DeadLetterStatus;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.IdempotentRecordStatus;
import com.uscdip.backend.model.NotificationDeliveryStatus;
import com.uscdip.backend.model.NotificationMessageStatus;
import com.uscdip.backend.model.PageResponse;
import com.uscdip.backend.repository.DeadLetterRepository;
import com.uscdip.backend.repository.NotificationDeliveryRepository;
import com.uscdip.backend.repository.NotificationMessageRepository;
import com.uscdip.backend.repository.OutboxEventRepository;
import com.uscdip.backend.repository.WorkOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final String MENU_WORKORDER_READ = "MENU:WORKORDER:READ";
    private static final String MENU_WORKORDER_DISPATCH = "MENU:WORKORDER:DISPATCH";
    private static final String AGGREGATE_WORK_ORDER = "WORK_ORDER";
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final Set<String> STABLE_WORK_ORDER_EVENTS = Set.of(
            OutboxService.EVENT_WORK_ORDER_CREATED,
            OutboxService.EVENT_WORK_ORDER_DISPATCHED,
            OutboxService.EVENT_WORK_ORDER_ACCEPTED,
            OutboxService.EVENT_WORK_ORDER_TRANSFERRED,
            OutboxService.EVENT_WORK_ORDER_COMPLETED,
            OutboxService.EVENT_WORK_ORDER_CLOSED,
            OutboxService.EVENT_WORK_ORDER_WRITEBACK_RECORDED,
            "WORK_ORDER_ESCALATED"
    );

    private final NotificationMessageRepository notificationMessageRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final IdempotentConsumerService idempotentConsumerService;
    private final ObjectScopeService objectScopeService;
    private final ObjectMapper objectMapper;
    private final List<String> channels;
    private final Set<String> failChannels;
    private final int maxAttempts;
    private final int retryBaseDelaySeconds;

    public NotificationService(
            NotificationMessageRepository notificationMessageRepository,
            NotificationDeliveryRepository notificationDeliveryRepository,
            OutboxEventRepository outboxEventRepository,
            WorkOrderRepository workOrderRepository,
            DeadLetterRepository deadLetterRepository,
            IdempotentConsumerService idempotentConsumerService,
            ObjectScopeService objectScopeService,
            ObjectMapper objectMapper,
            @Value("${backend.notification.channels:IN_APP,SMS,WECHAT}") String configuredChannels,
            @Value("${backend.notification.fail-channels:}") String configuredFailChannels,
            @Value("${backend.notification.max-attempts:3}") int maxAttempts,
            @Value("${backend.notification.retry-base-delay-seconds:30}") int retryBaseDelaySeconds
    ) {
        this.notificationMessageRepository = notificationMessageRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.workOrderRepository = workOrderRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.idempotentConsumerService = idempotentConsumerService;
        this.objectScopeService = objectScopeService;
        this.objectMapper = objectMapper;
        this.channels = parseList(configuredChannels, List.of("IN_APP", "SMS", "WECHAT"));
        this.failChannels = new LinkedHashSet<>(parseList(configuredFailChannels, List.of()));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBaseDelaySeconds = Math.max(1, retryBaseDelaySeconds);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listNotifications(
            AuthorizationContext context,
            int page,
            int pageSize,
            String status,
            String channel,
            String recipient,
            String sourceEventId,
            String workOrderId
    ) {
        List<NotificationMessageEntity> messages = notificationMessageRepository.findAllByOrderByCreatedAtDescNotificationIdDesc();
        Map<String, List<NotificationDeliveryEntity>> deliveryMap = loadDeliveryMap(messages);
        String normalizedStatus = normalize(status);
        String normalizedChannel = normalize(channel);
        String normalizedRecipient = normalize(recipient);
        String normalizedSourceEventId = normalize(sourceEventId);
        String normalizedWorkOrderId = normalize(workOrderId);

        List<NotificationResponse> visible = messages.stream()
                .filter(message -> isBlank(normalizedStatus) || normalizedStatus.equals(normalize(message.getStatus())))
                .filter(message -> isBlank(normalizedSourceEventId) || normalizedSourceEventId.equals(normalize(message.getSourceEventId())))
                .filter(message -> isBlank(normalizedWorkOrderId) || normalizedWorkOrderId.equals(normalize(message.getAggregateId())))
                .filter(message -> isBlank(normalizedRecipient)
                        || normalizedRecipient.equals(normalize(message.getRecipientUserId()))
                        || normalizedRecipient.equals(normalize(message.getRecipientUsername())))
                .filter(message -> isBlank(normalizedChannel) || deliveryMap.getOrDefault(message.getNotificationId(), List.of()).stream()
                        .anyMatch(delivery -> normalizedChannel.equals(normalize(delivery.getChannel()))))
                .filter(message -> canView(context, message))
                .map(message -> toResponse(message, deliveryMap.getOrDefault(message.getNotificationId(), List.of())))
                .toList();
        return paginate(visible, page, pageSize);
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotificationDetail(AuthorizationContext context, String notificationId) {
        NotificationMessageEntity message = requireNotification(notificationId);
        if (!canView(context, message)) {
            throw new AuthFlowException(ErrorCode.DATA_SCOPE_DENIED, HttpStatus.FORBIDDEN, ErrorCode.DATA_SCOPE_DENIED.defaultMessage());
        }
        return toResponse(message, notificationDeliveryRepository.findByNotificationIdOrderByChannelAsc(message.getNotificationId()));
    }

    @Transactional
    public NotificationConsumeResponse consumeStableWorkOrderOutbox(AuthorizationContext context) {
        requireDispatchAccess(context);
        List<OutboxEventEntity> events = outboxEventRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(this::isStableWorkOrderEvent)
                .toList();
        int consumed = 0;
        for (OutboxEventEntity event : events) {
            if (consumeStableWorkOrderEvent(event)) {
                consumed++;
            }
        }
        int retried = retryDueDeliveries();
        return new NotificationConsumeResponse(events.size(), consumed, retried);
    }

    @Transactional
    public NotificationResponse retryNotification(AuthorizationContext context, String notificationId) {
        requireDispatchAccess(context);
        NotificationMessageEntity message = requireNotification(notificationId);
        objectScopeService.requireAccess(context, ObjectScopeService.OBJECT_WORK_ORDER, message.getAggregateId(), MENU_WORKORDER_DISPATCH);
        List<NotificationDeliveryEntity> deliveries = notificationDeliveryRepository.findByNotificationIdOrderByChannelAsc(notificationId);
        LocalDateTime now = LocalDateTime.now();
        for (NotificationDeliveryEntity delivery : deliveries) {
            String status = normalize(delivery.getStatus());
            if (NotificationDeliveryStatus.FAILED.name().equals(status) || NotificationDeliveryStatus.DEAD.name().equals(status)) {
                attemptDelivery(message, delivery, now);
            }
        }
        updateMessageStatus(message, deliveries, LocalDateTime.now());
        return toResponse(message, notificationDeliveryRepository.findByNotificationIdOrderByChannelAsc(notificationId));
    }

    @Transactional
    public int retryDueDeliveries() {
        List<NotificationDeliveryEntity> dueDeliveries = notificationDeliveryRepository
                .findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(NotificationDeliveryStatus.FAILED.name(), LocalDateTime.now());
        Map<String, NotificationMessageEntity> messageMap = notificationMessageRepository.findAllById(
                        dueDeliveries.stream().map(NotificationDeliveryEntity::getNotificationId).collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(NotificationMessageEntity::getNotificationId, message -> message));
        LocalDateTime now = LocalDateTime.now();
        for (NotificationDeliveryEntity delivery : dueDeliveries) {
            NotificationMessageEntity message = messageMap.get(delivery.getNotificationId());
            if (message != null) {
                attemptDelivery(message, delivery, now);
            }
        }
        for (NotificationMessageEntity message : messageMap.values()) {
            updateMessageStatus(message, notificationDeliveryRepository.findByNotificationIdOrderByChannelAsc(message.getNotificationId()), LocalDateTime.now());
        }
        return dueDeliveries.size();
    }

    private boolean consumeStableWorkOrderEvent(OutboxEventEntity event) {
        if (notificationMessageRepository.findBySourceEventId(event.getEventId()).isPresent()) {
            return false;
        }
        JsonNode payload = parsePayload(event);
        String workOrderId = text(payload, "workOrderId", event.getAggregateId());
        Long versionNo = longValue(payload, "versionNo", 0L);
        IdempotentConsumerResult result = idempotentConsumerService.consume(
                IdempotentConsumerService.ACTION_SEND_NOTIFICATION,
                AGGREGATE_WORK_ORDER,
                workOrderId,
                event.getEventId(),
                versionNo,
                event.getPayload(),
                event.getTraceId(),
                () -> createNotificationIfAbsent(event, payload, workOrderId, versionNo)
        );
        return IdempotentRecordStatus.SUCCESS.name().equals(result.status()) && !result.duplicate();
    }

    private String createNotificationIfAbsent(OutboxEventEntity event, JsonNode payload, String workOrderId, Long versionNo) {
        return notificationMessageRepository.findBySourceEventId(event.getEventId())
                .map(NotificationMessageEntity::getNotificationId)
                .orElseGet(() -> createNotification(event, payload, workOrderId, versionNo));
    }

    private String createNotification(OutboxEventEntity event, JsonNode payload, String workOrderId, Long versionNo) {
        WorkOrderEntity workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.WORK_ORDER_NOT_FOUND, HttpStatus.NOT_FOUND, "Work order not found: " + workOrderId));
        LocalDateTime now = LocalDateTime.now();
        NotificationMessageEntity message = new NotificationMessageEntity();
        message.setNotificationId("NOTIFY-" + UUID.randomUUID());
        message.setSourceEventId(event.getEventId());
        message.setIdempotentKey(idempotentConsumerService.buildKey(workOrderId, IdempotentConsumerService.ACTION_SEND_NOTIFICATION, versionNo));
        message.setAggregateType(AGGREGATE_WORK_ORDER);
        message.setAggregateId(workOrderId);
        message.setEventType(normalize(event.getEventType()));
        message.setRecipientUserId(blankToNull(workOrder.getAssigneeUserId()));
        message.setRecipientUsername(blankToNull(workOrder.getAssignee()));
        message.setTitle(buildTitle(event.getEventType(), workOrder));
        message.setContent(buildContent(event, payload, workOrder));
        message.setStatus(NotificationMessageStatus.PARTIAL_FAILED.name());
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        notificationMessageRepository.save(message);

        List<NotificationDeliveryEntity> deliveries = new ArrayList<>();
        for (String channel : channels) {
            NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
            delivery.setDeliveryId("ND-" + UUID.randomUUID());
            delivery.setNotificationId(message.getNotificationId());
            delivery.setChannel(channel);
            delivery.setTarget(resolveTarget(channel, message));
            delivery.setStatus(NotificationDeliveryStatus.FAILED.name());
            delivery.setAttemptCount(0);
            delivery.setCreatedAt(now);
            delivery.setUpdatedAt(now);
            attemptDelivery(message, delivery, now);
            deliveries.add(delivery);
        }
        notificationDeliveryRepository.saveAll(deliveries);
        updateMessageStatus(message, deliveries, LocalDateTime.now());
        return message.getNotificationId();
    }

    private void attemptDelivery(NotificationMessageEntity message, NotificationDeliveryEntity delivery, LocalDateTime now) {
        int nextAttempt = safeAttemptCount(delivery.getAttemptCount()) + 1;
        delivery.setAttemptCount(nextAttempt);
        delivery.setUpdatedAt(now);
        if (shouldFail(delivery.getChannel(), nextAttempt)) {
            String error = "simulated notification channel failure: " + normalize(delivery.getChannel());
            delivery.setLastError(error);
            delivery.setSentAt(null);
            if (nextAttempt >= maxAttempts) {
                delivery.setStatus(NotificationDeliveryStatus.DEAD.name());
                delivery.setNextRetryAt(null);
                writeDeadLetter(message, delivery, error, now);
            } else {
                delivery.setStatus(NotificationDeliveryStatus.FAILED.name());
                delivery.setNextRetryAt(computeNextRetryAt(nextAttempt, now));
            }
            notificationDeliveryRepository.save(delivery);
            return;
        }
        delivery.setStatus(NotificationDeliveryStatus.SENT.name());
        delivery.setSentAt(now);
        delivery.setNextRetryAt(null);
        delivery.setLastError(null);
        notificationDeliveryRepository.save(delivery);
    }

    private void updateMessageStatus(NotificationMessageEntity message, List<NotificationDeliveryEntity> deliveries, LocalDateTime now) {
        boolean anyDead = deliveries.stream().anyMatch(delivery -> NotificationDeliveryStatus.DEAD.name().equals(normalize(delivery.getStatus())));
        boolean allSent = !deliveries.isEmpty() && deliveries.stream().allMatch(delivery -> NotificationDeliveryStatus.SENT.name().equals(normalize(delivery.getStatus())));
        if (allSent) {
            message.setStatus(NotificationMessageStatus.SENT.name());
            message.setCompletedAt(now);
            message.setLastError(null);
        } else if (anyDead) {
            message.setStatus(NotificationMessageStatus.DEAD.name());
            message.setCompletedAt(now);
            message.setLastError(joinErrors(deliveries));
        } else {
            message.setStatus(NotificationMessageStatus.PARTIAL_FAILED.name());
            message.setCompletedAt(null);
            message.setLastError(joinErrors(deliveries));
        }
        message.setUpdatedAt(now);
        notificationMessageRepository.save(message);
    }

    private void writeDeadLetter(NotificationMessageEntity message, NotificationDeliveryEntity delivery, String error, LocalDateTime now) {
        deadLetterRepository.save(new DeadLetterEntity(
                "DLQ-" + UUID.randomUUID(),
                message.getSourceEventId(),
                message.getIdempotentKey(),
                IdempotentConsumerService.ACTION_SEND_NOTIFICATION,
                message.getAggregateType(),
                message.getAggregateId(),
                "{\"notificationId\":\"" + message.getNotificationId() + "\",\"channel\":\"" + delivery.getChannel() + "\"}",
                null,
                abbreviateError(error),
                safeAttemptCount(delivery.getAttemptCount()),
                DeadLetterStatus.OPEN.name(),
                now,
                null
        ));
    }

    private boolean canView(AuthorizationContext context, NotificationMessageEntity message) {
        if (isRecipient(context, message)) {
            return true;
        }
        if (!AGGREGATE_WORK_ORDER.equals(normalize(message.getAggregateType()))) {
            return false;
        }
        return objectScopeService.filterAccessibleIds(
                context,
                ObjectScopeService.OBJECT_WORK_ORDER,
                List.of(message.getAggregateId()),
                MENU_WORKORDER_READ
        ).contains(message.getAggregateId());
    }

    private boolean isRecipient(AuthorizationContext context, NotificationMessageEntity message) {
        return equalsIgnoreCase(context.userId(), message.getRecipientUserId())
                || equalsIgnoreCase(context.username(), message.getRecipientUsername());
    }

    private void requireDispatchAccess(AuthorizationContext context) {
        if (!context.permissionCodes().contains(MENU_WORKORDER_DISPATCH)) {
            throw new AuthFlowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
        }
    }

    private boolean isStableWorkOrderEvent(OutboxEventEntity event) {
        return event != null
                && OutboxService.AGGREGATE_TYPE_WORK_ORDER.equals(normalize(event.getAggregateType()))
                && STABLE_WORK_ORDER_EVENTS.contains(normalize(event.getEventType()));
    }

    private NotificationMessageEntity requireNotification(String notificationId) {
        return notificationMessageRepository.findById(notificationId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.NOTIFICATION_NOT_FOUND, HttpStatus.NOT_FOUND, "Notification not found: " + notificationId));
    }

    private Map<String, List<NotificationDeliveryEntity>> loadDeliveryMap(List<NotificationMessageEntity> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        return notificationDeliveryRepository.findByNotificationIdIn(messages.stream()
                        .map(NotificationMessageEntity::getNotificationId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .stream()
                .sorted(Comparator.comparing(NotificationDeliveryEntity::getChannel))
                .collect(Collectors.groupingBy(NotificationDeliveryEntity::getNotificationId, LinkedHashMap::new, Collectors.toList()));
    }

    private NotificationResponse toResponse(NotificationMessageEntity message, List<NotificationDeliveryEntity> deliveries) {
        return new NotificationResponse(
                message.getNotificationId(),
                message.getSourceEventId(),
                message.getIdempotentKey(),
                message.getAggregateType(),
                message.getAggregateId(),
                message.getEventType(),
                message.getRecipientUserId(),
                message.getRecipientUsername(),
                message.getTitle(),
                message.getContent(),
                message.getStatus(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.getCompletedAt(),
                message.getLastError(),
                deliveries.stream().map(this::toDeliveryResponse).toList()
        );
    }

    private NotificationDeliveryResponse toDeliveryResponse(NotificationDeliveryEntity delivery) {
        return new NotificationDeliveryResponse(
                delivery.getDeliveryId(),
                delivery.getChannel(),
                delivery.getTarget(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getNextRetryAt(),
                delivery.getSentAt(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                delivery.getLastError()
        );
    }

    private PageResponse<NotificationResponse> paginate(List<NotificationResponse> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return PageResponse.of(List.of(), items.size(), safePage, safePageSize);
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePage, safePageSize);
    }

    private JsonNode parsePayload(OutboxEventEntity event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Outbox payload is invalid: " + event.getEventId(), ex);
        }
    }

    private String buildTitle(String eventType, WorkOrderEntity workOrder) {
        return switch (normalize(eventType)) {
            case OutboxService.EVENT_WORK_ORDER_CREATED -> "工单已创建：" + workOrder.getWorkOrderId();
            case OutboxService.EVENT_WORK_ORDER_DISPATCHED -> "工单已派发：" + workOrder.getWorkOrderId();
            case OutboxService.EVENT_WORK_ORDER_ACCEPTED -> "工单已接单：" + workOrder.getWorkOrderId();
            case OutboxService.EVENT_WORK_ORDER_TRANSFERRED -> "工单已转派：" + workOrder.getWorkOrderId();
            case OutboxService.EVENT_WORK_ORDER_COMPLETED -> "工单已完成：" + workOrder.getWorkOrderId();
            case OutboxService.EVENT_WORK_ORDER_CLOSED -> "工单已关闭：" + workOrder.getWorkOrderId();
            case OutboxService.EVENT_WORK_ORDER_WRITEBACK_RECORDED -> "工单已回写：" + workOrder.getWorkOrderId();
            case "WORK_ORDER_ESCALATED" -> "工单已升级：" + workOrder.getWorkOrderId();
            default -> "工单通知：" + workOrder.getWorkOrderId();
        };
    }

    private String buildContent(OutboxEventEntity event, JsonNode payload, WorkOrderEntity workOrder) {
        return "eventType=" + normalize(event.getEventType())
                + ", workOrderId=" + workOrder.getWorkOrderId()
                + ", incidentId=" + workOrder.getIncidentId()
                + ", status=" + text(payload, "status", workOrder.getStatus())
                + ", assignee=" + text(payload, "assignee", workOrder.getAssignee());
    }

    private String resolveTarget(String channel, NotificationMessageEntity message) {
        String target = defaultString(message.getRecipientUserId(), message.getRecipientUsername());
        if (isBlank(target)) {
            target = message.getAggregateId();
        }
        return switch (normalize(channel)) {
            case "IN_APP" -> "in-app://" + target;
            case "SMS" -> "sms://" + target;
            case "WECHAT" -> "wechat://" + target;
            case "EMAIL" -> "email://" + target;
            default -> normalize(channel).toLowerCase(Locale.ROOT) + "://" + target;
        };
    }

    private boolean shouldFail(String channel, int attemptNo) {
        String normalizedChannel = normalize(channel);
        return failChannels.contains(normalizedChannel + "_ALWAYS")
                || (attemptNo == 1 && failChannels.contains(normalizedChannel));
    }

    private LocalDateTime computeNextRetryAt(int attemptCount, LocalDateTime now) {
        long delaySeconds = (long) retryBaseDelaySeconds * (1L << Math.max(0, attemptCount - 1));
        return now.plusSeconds(delaySeconds);
    }

    private String joinErrors(List<NotificationDeliveryEntity> deliveries) {
        String joined = deliveries.stream()
                .filter(delivery -> !NotificationDeliveryStatus.SENT.name().equals(normalize(delivery.getStatus())))
                .map(delivery -> delivery.getChannel() + ":" + defaultString(delivery.getLastError(), delivery.getStatus()))
                .collect(Collectors.joining("; "));
        return isBlank(joined) ? null : abbreviateError(joined);
    }

    private List<String> parseList(String value, List<String> fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        List<String> parsed = java.util.Arrays.stream(value.split(","))
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        return parsed.isEmpty() ? fallback : parsed;
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node != null && node.hasNonNull(field) && !node.path(field).asText().isBlank()) {
            return node.path(field).asText();
        }
        return fallback;
    }

    private Long longValue(JsonNode node, String field, Long fallback) {
        if (node != null && node.hasNonNull(field) && node.path(field).canConvertToLong()) {
            return node.path(field).asLong();
        }
        return fallback;
    }

    private int safeAttemptCount(Integer attemptCount) {
        return attemptCount == null ? 0 : attemptCount;
    }

    private String abbreviateError(String errorMessage) {
        String value = errorMessage == null || errorMessage.isBlank() ? "Unknown notification failure" : errorMessage;
        if (value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultString(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
