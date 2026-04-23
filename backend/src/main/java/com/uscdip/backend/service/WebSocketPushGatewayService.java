package com.uscdip.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.config.WebSocketPushProperties;
import com.uscdip.backend.dto.TopicAuthorizationRequest;
import com.uscdip.backend.dto.WebSocketAckRequest;
import com.uscdip.backend.dto.WebSocketPushPayload;
import com.uscdip.backend.entity.NotificationMessageEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.WebSocketAckEntity;
import com.uscdip.backend.entity.WebSocketConnectionEntity;
import com.uscdip.backend.entity.WebSocketPushMessageEntity;
import com.uscdip.backend.entity.WebSocketSubscriptionEntity;
import com.uscdip.backend.exception.AuthFlowException;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.ErrorCode;
import com.uscdip.backend.model.WebSocketConnectionStatus;
import com.uscdip.backend.model.WebSocketSubscriptionStatus;
import com.uscdip.backend.model.WebSocketUserPrincipal;
import com.uscdip.backend.repository.NotificationMessageRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.WebSocketAckRepository;
import com.uscdip.backend.repository.WebSocketConnectionRepository;
import com.uscdip.backend.repository.WebSocketPushMessageRepository;
import com.uscdip.backend.repository.WebSocketSubscriptionRepository;
import com.uscdip.backend.support.TraceIdContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebSocketPushGatewayService {

    public static final String ENTRY_EMGC = "ENTRY:EMGC";
    public static final String MENU_WORKORDER_READ = "MENU:WORKORDER:READ";
    public static final String USER_QUEUE_TOPIC_SUFFIX = ".workorder.notifications";
    private static final String OBJECT_WORK_ORDER = "WORK_ORDER";
    private static final String PUSH_TYPE_WORK_ORDER_NOTIFICATION = "WORK_ORDER_NOTIFICATION";

    private final WebSocketPushProperties properties;
    private final WebSocketConnectionRepository connectionRepository;
    private final WebSocketSubscriptionRepository subscriptionRepository;
    private final WebSocketPushMessageRepository pushMessageRepository;
    private final WebSocketAckRepository ackRepository;
    private final NotificationMessageRepository notificationMessageRepository;
    private final ObjectScopeBindingRepository objectScopeBindingRepository;
    private final LocalAccessTokenService localAccessTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final AuthorizationService authorizationService;
    private final TopicAuthorizationService topicAuthorizationService;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final ObjectMapper objectMapper;

    public WebSocketPushGatewayService(
            WebSocketPushProperties properties,
            WebSocketConnectionRepository connectionRepository,
            WebSocketSubscriptionRepository subscriptionRepository,
            WebSocketPushMessageRepository pushMessageRepository,
            WebSocketAckRepository ackRepository,
            NotificationMessageRepository notificationMessageRepository,
            ObjectScopeBindingRepository objectScopeBindingRepository,
            LocalAccessTokenService localAccessTokenService,
            TokenRevocationService tokenRevocationService,
            AuthorizationService authorizationService,
            TopicAuthorizationService topicAuthorizationService,
            ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.connectionRepository = connectionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.pushMessageRepository = pushMessageRepository;
        this.ackRepository = ackRepository;
        this.notificationMessageRepository = notificationMessageRepository;
        this.objectScopeBindingRepository = objectScopeBindingRepository;
        this.localAccessTokenService = localAccessTokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.authorizationService = authorizationService;
        this.topicAuthorizationService = topicAuthorizationService;
        this.messagingTemplateProvider = messagingTemplateProvider;
        this.objectMapper = objectMapper;
    }

    public boolean isOriginAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        return properties.getAllowedOrigins().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .anyMatch(value -> "*".equals(value) || value.equalsIgnoreCase(origin));
    }

    @Transactional
    public WebSocketUserPrincipal authenticateHandshake(String token, String clientIp, String userAgent) {
        if (token == null || token.isBlank()) {
            throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "WebSocket access token is required");
        }
        LocalAccessTokenService.AccessTokenPrincipal tokenPrincipal = localAccessTokenService.verify(token);
        tokenRevocationService.validateAccessToken(tokenPrincipal, clientIp, userAgent);
        rejectIfConnectionLimitExceeded(tokenPrincipal.userId());
        LocalDateTime now = LocalDateTime.now();
        String connectionId = "WSC-" + UUID.randomUUID();
        WebSocketConnectionEntity connection = new WebSocketConnectionEntity();
        connection.setConnectionId(connectionId);
        connection.setUserId(tokenPrincipal.userId());
        connection.setUsername(tokenPrincipal.username());
        connection.setClientIp(truncate(clientIp, 64));
        connection.setUserAgent(truncate(userAgent, 512));
        connection.setStatus(WebSocketConnectionStatus.CONNECTED.name());
        connection.setConnectedAt(now);
        connection.setLastSeenAt(now);
        connectionRepository.save(connection);
        return new WebSocketUserPrincipal(tokenPrincipal.userId(), tokenPrincipal.userId(), tokenPrincipal.username(), connectionId);
    }

    @Transactional
    public void attachStompSession(WebSocketUserPrincipal principal, String stompSessionId) {
        if (principal == null || stompSessionId == null || stompSessionId.isBlank()) {
            return;
        }
        connectionRepository.findById(principal.connectionId()).ifPresent(connection -> {
            connection.setStompSessionId(stompSessionId);
            connection.setLastSeenAt(LocalDateTime.now());
            connectionRepository.save(connection);
        });
    }

    @Transactional
    public void heartbeat(WebSocketUserPrincipal principal, String stompSessionId) {
        findConnection(principal, stompSessionId).ifPresent(connection -> {
            connection.setLastSeenAt(LocalDateTime.now());
            connectionRepository.save(connection);
        });
    }

    @Transactional
    public void subscribe(WebSocketUserPrincipal principal, String stompSessionId, String subscriptionId, String destination) {
        String topic = normalizeDestinationTopic(destination, principal == null ? null : principal.userId());
        AuthorizationContext context = requireContext(principal);
        authorizeTopic(context, topic);
        WebSocketConnectionEntity connection = findConnection(principal, stompSessionId)
                .orElseThrow(() -> new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "WebSocket connection is not active"));
        LocalDateTime now = LocalDateTime.now();
        WebSocketSubscriptionEntity subscription = new WebSocketSubscriptionEntity();
        subscription.setSubscriptionRowId("WSS-" + UUID.randomUUID());
        subscription.setConnectionId(connection.getConnectionId());
        subscription.setStompSessionId(stompSessionId);
        subscription.setSubscriptionId(subscriptionId);
        subscription.setUserId(context.userId());
        subscription.setTopic(topic);
        subscription.setStatus(WebSocketSubscriptionStatus.ACTIVE.name());
        subscription.setSubscribedAt(now);
        subscriptionRepository.save(subscription);
        connection.setLastSeenAt(now);
        connectionRepository.save(connection);
        replayUnackedMessages(context.userId(), topic);
    }

    @Transactional
    public WebSocketAckEntity ack(WebSocketUserPrincipal principal, WebSocketAckRequest request) {
        String topic = normalizeDestinationTopic(request.topic(), principal == null ? null : principal.userId());
        AuthorizationContext context = requireContext(principal);
        authorizeTopic(context, topic);
        LocalDateTime now = LocalDateTime.now();
        long lastAckSeq = Math.max(0L, request.lastAckSeq() == null ? 0L : request.lastAckSeq());
        WebSocketAckEntity ack = ackRepository.findByUserIdAndTopic(context.userId(), topic)
                .orElseGet(() -> {
                    WebSocketAckEntity created = new WebSocketAckEntity();
                    created.setAckId(buildAckId(context.userId(), topic));
                    created.setUserId(context.userId());
                    created.setTopic(topic);
                    created.setLastAckSeq(0L);
                    return created;
                });
        ack.setLastAckSeq(Math.max(ack.getLastAckSeq() == null ? 0L : ack.getLastAckSeq(), lastAckSeq));
        ack.setUpdatedAt(now);
        heartbeat(principal, null);
        return ackRepository.save(ack);
    }

    @Transactional
    public void disconnect(String stompSessionId, String reason) {
        if (stompSessionId == null || stompSessionId.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        connectionRepository.findByStompSessionIdAndStatus(stompSessionId, WebSocketConnectionStatus.CONNECTED.name())
                .ifPresent(connection -> closeConnection(connection, reason, now));
        closeSubscriptions(subscriptionRepository.findByStompSessionIdAndStatus(stompSessionId, WebSocketSubscriptionStatus.ACTIVE.name()), now);
    }

    @Transactional
    public int closeIdleConnections() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(Math.max(1, properties.getIdleTimeoutSeconds()));
        List<WebSocketConnectionEntity> idleConnections = connectionRepository.findByStatusAndLastSeenAtBefore(
                WebSocketConnectionStatus.CONNECTED.name(),
                cutoff
        );
        LocalDateTime now = LocalDateTime.now();
        for (WebSocketConnectionEntity connection : idleConnections) {
            closeConnection(connection, "IDLE_TIMEOUT", now);
            closeSubscriptions(subscriptionRepository.findByConnectionIdAndStatus(
                    connection.getConnectionId(),
                    WebSocketSubscriptionStatus.ACTIVE.name()
            ), now);
        }
        return idleConnections.size();
    }

    @Transactional
    public int bridgeNotificationsToPushMessages() {
        int created = 0;
        for (NotificationMessageEntity notification : notificationMessageRepository.findAllByOrderByCreatedAtDescNotificationIdDesc()) {
            created += createMessagesForNotification(notification);
        }
        return created;
    }

    @Transactional
    public int createMessagesForNotification(NotificationMessageEntity notification) {
        if (notification == null || !OBJECT_WORK_ORDER.equalsIgnoreCase(notification.getAggregateType())) {
            return 0;
        }
        List<String> topics = resolveNotificationTopics(notification);
        int created = 0;
        for (String topic : topics) {
            if (pushMessageRepository.findBySourceNotificationIdAndTopic(notification.getNotificationId(), topic).isPresent()) {
                continue;
            }
            WebSocketPushMessageEntity message = new WebSocketPushMessageEntity();
            message.setMessageId("WSP-" + UUID.randomUUID());
            message.setTopic(topic);
            message.setRecipientUserId(notification.getRecipientUserId());
            message.setSourceNotificationId(notification.getNotificationId());
            message.setTraceId(notification.getTraceId());
            message.setMessageType(PUSH_TYPE_WORK_ORDER_NOTIFICATION);
            message.setTitle(notification.getTitle());
            message.setContent(notification.getContent());
            message.setCreatedAt(LocalDateTime.now());
            message.setPayload("{}");
            message = pushMessageRepository.saveAndFlush(message);
            message.setPayload(toPayloadJson(message));
            pushMessageRepository.save(message);
            push(message);
            created++;
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<WebSocketPushPayload> replayPreview(String userId, String topic) {
        String normalizedTopic = normalizeDestinationTopic(topic, userId);
        long lastAckSeq = ackRepository.findByUserIdAndTopic(userId, normalizedTopic)
                .map(WebSocketAckEntity::getLastAckSeq)
                .orElse(0L);
        return pushMessageRepository.findByTopicAndSeqNoGreaterThanOrderBySeqNoAsc(
                        normalizedTopic,
                        lastAckSeq,
                        PageRequest.of(0, Math.max(1, properties.getReplayBatchSize()))
                ).stream()
                .map(this::toPayload)
                .toList();
    }

    public String normalizeDestinationTopic(String destination, String userId) {
        if (destination == null || destination.isBlank()) {
            throw new AuthFlowException(ErrorCode.INVALID_PARAMETER, HttpStatus.BAD_REQUEST, "WebSocket destination is required");
        }
        String value = destination.trim();
        if (value.startsWith("/topic/")) {
            return value.substring("/topic/".length());
        }
        if (value.startsWith("/user/queue/workorder.notifications")) {
            if (userId == null || userId.isBlank()) {
                throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "User destination requires authentication");
            }
            return "user." + userId + USER_QUEUE_TOPIC_SUFFIX;
        }
        if (value.startsWith("region.") || value.startsWith("user.")) {
            return value;
        }
        throw new AuthFlowException(ErrorCode.SUBSCRIPTION_NOT_ALLOWED, HttpStatus.FORBIDDEN, "Unsupported WebSocket topic: " + destination);
    }

    public boolean isAckDestinationAllowed(String destination) {
        return "/app/push/ack".equals(destination) || "/app/push/heartbeat".equals(destination);
    }

    private void rejectIfConnectionLimitExceeded(String userId) {
        if (connectionRepository.countByStatus(WebSocketConnectionStatus.CONNECTED.name()) >= Math.max(1, properties.getMaxConnections())) {
            throw new AuthFlowException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, "WebSocket connection limit exceeded");
        }
        if (connectionRepository.countByUserIdAndStatus(userId, WebSocketConnectionStatus.CONNECTED.name())
                >= Math.max(1, properties.getMaxConnectionsPerUser())) {
            throw new AuthFlowException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, "WebSocket per-user connection limit exceeded");
        }
    }

    private AuthorizationContext requireContext(WebSocketUserPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId().isBlank()) {
            throw new AuthFlowException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
        }
        return authorizationService.getAuthorizationContext(principal.userId())
                .orElseThrow(() -> new AuthFlowException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + principal.userId()));
    }

    private void authorizeTopic(AuthorizationContext context, String topic) {
        String regionId = parseRegionId(topic);
        String assignee = topic.startsWith("user.") ? context.userId() : null;
        topicAuthorizationService.subscribe(context, new TopicAuthorizationRequest(
                context.userId(),
                ENTRY_EMGC,
                MENU_WORKORDER_READ,
                regionId,
                assignee,
                "DETAIL",
                List.of(topic)
        ));
    }

    private Optional<WebSocketConnectionEntity> findConnection(WebSocketUserPrincipal principal, String stompSessionId) {
        if (principal != null && principal.connectionId() != null) {
            Optional<WebSocketConnectionEntity> byId = connectionRepository.findById(principal.connectionId())
                    .filter(connection -> WebSocketConnectionStatus.CONNECTED.name().equals(connection.getStatus()));
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (stompSessionId != null && !stompSessionId.isBlank()) {
            return connectionRepository.findByStompSessionIdAndStatus(stompSessionId, WebSocketConnectionStatus.CONNECTED.name());
        }
        return Optional.empty();
    }

    private void replayUnackedMessages(String userId, String topic) {
        for (WebSocketPushPayload payload : replayPreview(userId, topic)) {
            sendPayload(topic, userId, payload);
        }
    }

    private void push(WebSocketPushMessageEntity message) {
        sendPayload(message.getTopic(), message.getRecipientUserId(), toPayload(message));
    }

    private void sendPayload(String topic, String userId, WebSocketPushPayload payload) {
        TraceIdContext.runWithTraceId(payload.traceId(), () -> {
            if (topic.startsWith("user.")) {
                String targetUserId = topic.substring("user.".length(), topic.length() - USER_QUEUE_TOPIC_SUFFIX.length());
                messagingTemplateProvider.getIfAvailable().convertAndSendToUser(targetUserId, "/queue/workorder.notifications", payload);
                return;
            }
            messagingTemplateProvider.getIfAvailable().convertAndSend("/topic/" + topic, payload);
        });
    }

    private void closeConnection(WebSocketConnectionEntity connection, String reason, LocalDateTime now) {
        connection.setStatus(WebSocketConnectionStatus.DISCONNECTED.name());
        connection.setDisconnectedAt(now);
        connection.setLastSeenAt(now);
        connection.setCloseReason(truncate(reason, 255));
        connectionRepository.save(connection);
    }

    private void closeSubscriptions(List<WebSocketSubscriptionEntity> subscriptions, LocalDateTime now) {
        for (WebSocketSubscriptionEntity subscription : subscriptions) {
            subscription.setStatus(WebSocketSubscriptionStatus.CLOSED.name());
            subscription.setClosedAt(now);
            subscriptionRepository.save(subscription);
        }
    }

    private List<String> resolveNotificationTopics(NotificationMessageEntity notification) {
        List<String> topics = new ArrayList<>();
        if (notification.getRecipientUserId() != null && !notification.getRecipientUserId().isBlank()) {
            topics.add("user." + notification.getRecipientUserId() + USER_QUEUE_TOPIC_SUFFIX);
        }
        objectScopeBindingRepository.findByObjectTypeAndObjectId(OBJECT_WORK_ORDER, notification.getAggregateId())
                .map(ObjectScopeBindingEntity::getRegionId)
                .filter(Objects::nonNull)
                .filter(regionId -> !regionId.isBlank())
                .map(regionId -> "region." + regionId + ".workorder.notifications")
                .ifPresent(topics::add);
        return topics.stream().distinct().toList();
    }

    private WebSocketPushPayload toPayload(WebSocketPushMessageEntity message) {
        return new WebSocketPushPayload(
                message.getSeqNo(),
                message.getMessageId(),
                message.getTopic(),
                message.getMessageType(),
                message.getSourceNotificationId(),
                message.getTraceId(),
                message.getTitle(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private String toPayloadJson(WebSocketPushMessageEntity message) {
        try {
            return objectMapper.writeValueAsString(toPayload(message));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize WebSocket push payload", ex);
        }
    }

    private String parseRegionId(String topic) {
        if (!topic.startsWith("region.")) {
            return null;
        }
        String remainder = topic.substring("region.".length());
        int separator = remainder.indexOf('.');
        return separator < 0 ? remainder.toUpperCase(Locale.ROOT) : remainder.substring(0, separator).toUpperCase(Locale.ROOT);
    }

    private String buildAckId(String userId, String topic) {
        return (userId + ":" + topic).replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
