package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.dto.WebSocketAckRequest;
import com.uscdip.backend.entity.NotificationMessageEntity;
import com.uscdip.backend.entity.OutboxEventEntity;
import com.uscdip.backend.entity.TraceLinkEventEntity;
import com.uscdip.backend.entity.WebSocketPushMessageEntity;
import com.uscdip.backend.model.AuthorizationContext;
import com.uscdip.backend.model.WebSocketUserPrincipal;
import com.uscdip.backend.repository.NotificationMessageRepository;
import com.uscdip.backend.repository.OutboxEventRepository;
import com.uscdip.backend.repository.TraceLinkEventRepository;
import com.uscdip.backend.repository.WebSocketPushMessageRepository;
import com.uscdip.backend.service.AuthorizationService;
import com.uscdip.backend.service.LocalTokenService;
import com.uscdip.backend.service.NotificationService;
import com.uscdip.backend.service.OutboxService;
import com.uscdip.backend.service.OutboxRelayService;
import com.uscdip.backend.service.WebSocketPushGatewayService;
import com.uscdip.backend.support.TraceIdContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000",
        "backend.notification.retry-fixed-delay-ms=3600000",
        "backend.websocket.idle-scan-fixed-delay-ms=3600000",
        "backend.websocket.notification-bridge-fixed-delay-ms=3600000",
        "backend.websocket.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
class TraceLinkInstrumentationIntegrationTest {

    private static final Pattern GENERATED_TRACE_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TraceLinkEventRepository traceLinkEventRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationMessageRepository notificationMessageRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private WebSocketPushGatewayService gatewayService;

    @Autowired
    private WebSocketPushMessageRepository webSocketPushMessageRepository;

    @Test
    void httpRequestCarriesProvidedTraceIdIntoResponseAndTraceTable() throws Exception {
        String traceId = "TRACE-B28-HTTP-001";

        mockMvc.perform(get("/api/menu-boundaries")
                        .header(TraceIdContext.TRACE_ID_HEADER, traceId))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, traceId))
                .andExpect(jsonPath("$.traceId").value(traceId));

        List<TraceLinkEventEntity> events = traceLinkEventRepository.findByTraceIdOrderByEventTimeAscEventIdAsc(traceId);
        Assertions.assertTrue(events.stream().anyMatch(event -> "HTTP_REQUEST_RECEIVED".equals(event.getEventType())));
        Assertions.assertTrue(events.stream().anyMatch(event -> "HTTP_REQUEST_COMPLETED".equals(event.getEventType())));
    }

    @Test
    void traceparentIsMappedAndInvalidTraceIdIsIgnored() throws Exception {
        String traceparent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";

        MvcResult result = mockMvc.perform(get("/api/menu-boundaries")
                        .header(TraceIdContext.TRACE_ID_HEADER, "###bad###")
                        .header(TraceIdContext.TRACEPARENT_HEADER, traceparent))
                .andExpect(status().isOk())
                .andReturn();

        String resolvedTraceId = result.getResponse().getHeader(TraceIdContext.TRACE_ID_HEADER);
        Assertions.assertEquals("0123456789abcdef0123456789abcdef", resolvedTraceId);
        Assertions.assertTrue(GENERATED_TRACE_PATTERN.matcher(resolvedTraceId).matches());
        Assertions.assertFalse(traceLinkEventRepository.findByTraceIdOrderByEventTimeAscEventIdAsc(resolvedTraceId).isEmpty());
    }

    @Test
    void workOrderOutboxNotificationAndWebSocketPushShareTraceId() throws Exception {
        String traceId = "TRACE-B28-E2E-001";
        TokenPairResponse dispatcherToken = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");
        TokenPairResponse inspectorToken = localTokenService.issueForUser("U-INSPECT-001", "127.0.0.1", "JUnit");

        MvcResult createResult = mockMvc.perform(post("/api/workorders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "incidentId", "INC-ALERT-SEED-001",
                                "workOrderType", "INCIDENT_RESPONSE",
                                "priority", "HIGH",
                                "description", "B-28 trace e2e work order",
                                "assigneeUserId", "U-INSPECT-001",
                                "assignee", "zhangsan",
                                "slaDueAt", "2026-04-25T12:00:00"
                        )))
                        .header("Authorization", "Bearer " + dispatcherToken.accessToken())
                        .header(TraceIdContext.TRACE_ID_HEADER, traceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andReturn();

        String workOrderId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("workOrderId").asText();
        OutboxEventEntity createdEvent = outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                        OutboxService.AGGREGATE_TYPE_WORK_ORDER,
                        workOrderId
                ).stream()
                .filter(event -> OutboxService.EVENT_WORK_ORDER_CREATED.equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(traceId, createdEvent.getTraceId());
        JsonNode payload = objectMapper.readTree(createdEvent.getPayload());
        Assertions.assertEquals(traceId, payload.path("traceId").asText());

        outboxRelayService.relayPendingEventsNow();

        AuthorizationContext dispatcherContext = authorizationService.getAuthorizationContext("U-DISPATCH-001").orElseThrow();
        notificationService.consumeStableWorkOrderOutbox(dispatcherContext);
        NotificationMessageEntity notification = notificationMessageRepository.findBySourceEventId(createdEvent.getEventId()).orElseThrow();
        Assertions.assertEquals(traceId, notification.getTraceId());

        WebSocketUserPrincipal principal = gatewayService.authenticateHandshake(
                inspectorToken.accessToken(),
                "127.0.0.1",
                "JUnit",
                traceId
        );
        TraceIdContext.runWithTraceId(traceId, () -> gatewayService.attachStompSession(principal, "stomp-b28-001"));
        TraceIdContext.runWithTraceId(traceId, () -> gatewayService.subscribe(principal, "stomp-b28-001", "sub-b28-001", "/user/queue/workorder.notifications"));

        gatewayService.bridgeNotificationsToPushMessages();
        WebSocketPushMessageEntity pushMessage = webSocketPushMessageRepository.findAll().stream()
                .filter(message -> notification.getNotificationId().equals(message.getSourceNotificationId()))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(traceId, pushMessage.getTraceId());

        TraceIdContext.runWithTraceId(traceId, () ->
                gatewayService.ack(principal, new WebSocketAckRequest(pushMessage.getTopic(), pushMessage.getSeqNo())));
        TraceIdContext.runWithTraceId(traceId, () -> gatewayService.disconnect("stomp-b28-001", "TEST_DISCONNECT"));

        List<TraceLinkEventEntity> traceEvents = traceLinkEventRepository.findByTraceIdOrderByEventTimeAscEventIdAsc(traceId);
        Assertions.assertTrue(traceEvents.stream().anyMatch(event -> "HTTP".equals(event.getStage())));
        Assertions.assertTrue(traceEvents.stream().anyMatch(event -> "OUTBOX_DISPATCH_SUCCESS".equals(event.getEventType())));
        Assertions.assertTrue(traceEvents.stream().anyMatch(event -> "NOTIFICATION_CONSUME_SUCCESS".equals(event.getEventType())));
        Assertions.assertTrue(traceEvents.stream().anyMatch(event -> "WS_PUSH".equals(event.getEventType())));
        Assertions.assertTrue(traceEvents.stream().anyMatch(event -> "WS_ACK".equals(event.getEventType())));
        Assertions.assertTrue(traceEvents.stream().anyMatch(event -> "WS_DISCONNECT".equals(event.getEventType())));
    }

    @Test
    void traceApiRequiresPlatformAdminAndSupportsFilters() throws Exception {
        String traceId = "TRACE-B28-ADMIN-001";
        TokenPairResponse regularToken = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/menu-boundaries")
                        .header(TraceIdContext.TRACE_ID_HEADER, traceId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/traces")
                        .header("Authorization", "Bearer " + regularToken.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/traces")
                        .param("traceId", traceId)
                        .param("stage", "HTTP")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].traceId").value(traceId))
                .andExpect(jsonPath("$.data.items[0].stage").value("HTTP"));

        mockMvc.perform(get("/api/traces/{traceId}", traceId)
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].traceId").value(traceId));
    }
}
