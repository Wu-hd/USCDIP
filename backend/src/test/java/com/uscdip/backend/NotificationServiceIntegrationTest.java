package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.NotificationMessageEntity;
import com.uscdip.backend.entity.OutboxEventEntity;
import com.uscdip.backend.model.IdempotentRecordStatus;
import com.uscdip.backend.model.OutboxEventStatus;
import com.uscdip.backend.repository.IdempotentRecordRepository;
import com.uscdip.backend.repository.NotificationDeliveryRepository;
import com.uscdip.backend.repository.NotificationMessageRepository;
import com.uscdip.backend.repository.OutboxEventRepository;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000",
        "backend.notification.retry-fixed-delay-ms=3600000",
        "backend.notification.channels=IN_APP,SMS,WECHAT",
        "backend.notification.fail-channels=SMS",
        "backend.notification.retry-base-delay-seconds=1"
})
@AutoConfigureMockMvc
class NotificationServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private NotificationMessageRepository notificationMessageRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private IdempotentRecordRepository idempotentRecordRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void consumeStableWorkOrderOutboxIsIdempotentAndDoesNotConsumeIncidentEvents() throws Exception {
        TokenPairResponse dispatcherToken = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");
        String workOrderId = createWorkOrder(dispatcherToken, "B-23 notification idempotency");

        mockMvc.perform(post("/api/notifications/consume-outbox")
                        .header("Authorization", "Bearer " + dispatcherToken.accessToken()))
                .andExpect(status().isOk());

        NotificationMessageEntity message = notificationMessageRepository.findAllByOrderByCreatedAtDescNotificationIdDesc().stream()
                .filter(item -> workOrderId.equals(item.getAggregateId()))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals("WORK_ORDER", message.getAggregateType());
        Assertions.assertEquals("PARTIAL_FAILED", message.getStatus());
        Assertions.assertEquals(3, notificationDeliveryRepository.findByNotificationIdOrderByChannelAsc(message.getNotificationId()).size());
        Assertions.assertEquals(
                IdempotentRecordStatus.SUCCESS.name(),
                idempotentRecordRepository.findById(workOrderId.toUpperCase() + ":SEND_NOTIFICATION:1").orElseThrow().getStatus()
        );

        mockMvc.perform(post("/api/notifications/consume-outbox")
                        .header("Authorization", "Bearer " + dispatcherToken.accessToken()))
                .andExpect(status().isOk());

        long notificationCount = notificationMessageRepository.findAllByOrderByCreatedAtDescNotificationIdDesc().stream()
                .filter(item -> workOrderId.equals(item.getAggregateId()))
                .count();
        Assertions.assertEquals(1, notificationCount);
        Assertions.assertTrue(notificationMessageRepository.findAllByOrderByCreatedAtDescNotificationIdDesc().stream()
                .noneMatch(item -> "INCIDENT".equals(item.getAggregateType())));
    }

    @Test
    void retryFailedChannelTurnsNotificationSent() throws Exception {
        TokenPairResponse dispatcherToken = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");
        String workOrderId = createWorkOrder(dispatcherToken, "B-23 retry notification");
        consume(dispatcherToken);
        String notificationId = notificationMessageRepository.findAllByOrderByCreatedAtDescNotificationIdDesc().stream()
                .filter(item -> workOrderId.equals(item.getAggregateId()))
                .findFirst()
                .orElseThrow()
                .getNotificationId();

        mockMvc.perform(post("/api/notifications/{notificationId}/retry", notificationId)
                        .header("Authorization", "Bearer " + dispatcherToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.deliveries[?(@.channel=='SMS')].status").value("SENT"));
    }

    @Test
    void notificationQueryRespectsWorkOrderScopeAndRecipient() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");
        TokenPairResponse inspectorToken = localTokenService.issueForUser("U-INSPECT-001", "127.0.0.1", "JUnit");
        saveWorkOrderOutbox("OBE-B23-BINJIANG-001", "WO-B22-ACCEPTED-001", "WORK_ORDER_ACCEPTED", 3, "lisi");
        consume(adminToken);

        MvcResult regionalResult = mockMvc.perform(get("/api/notifications")
                        .param("page", "1")
                        .param("pageSize", "100")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode regionalItems = objectMapper.readTree(regionalResult.getResponse().getContentAsString()).path("data").path("items");
        Assertions.assertFalse(containsAggregate(regionalItems, "WO-B22-ACCEPTED-001"));

        MvcResult inspectorResult = mockMvc.perform(get("/api/notifications")
                        .param("recipient", "zhangsan")
                        .param("page", "1")
                        .param("pageSize", "100")
                        .header("Authorization", "Bearer " + inspectorToken.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode inspectorItems = objectMapper.readTree(inspectorResult.getResponse().getContentAsString()).path("data").path("items");
        Assertions.assertTrue(containsAggregate(inspectorItems, "WO-B22-DISPATCHED-001"));
        Assertions.assertFalse(containsAggregate(inspectorItems, "WO-B22-ACCEPTED-001"));
    }

    private String createWorkOrder(TokenPairResponse token, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workorders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "incidentId", "INC-ALERT-SEED-001",
                                "workOrderType", "INCIDENT_RESPONSE",
                                "priority", "HIGH",
                                "description", description,
                                "assigneeUserId", "U-INSPECT-001",
                                "assignee", "zhangsan",
                                "slaDueAt", "2026-04-25T12:00:00"
                        )))
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("workOrderId").asText();
    }

    private void consume(TokenPairResponse token) throws Exception {
        mockMvc.perform(post("/api/notifications/consume-outbox")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk());
    }

    private void saveWorkOrderOutbox(String eventId, String workOrderId, String eventType, long versionNo, String assignee) {
        LocalDateTime now = LocalDateTime.now();
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId(eventId);
        event.setAggregateType("WORK_ORDER");
        event.setAggregateId(workOrderId);
        event.setEventType(eventType);
        event.setPayload("{\"workOrderId\":\"" + workOrderId + "\",\"status\":\"ACCEPTED\",\"assignee\":\"" + assignee + "\",\"versionNo\":" + versionNo + "}");
        event.setTraceId("TRACE-" + eventId);
        event.setStatus(OutboxEventStatus.NEW.name());
        event.setRetryCount(0);
        event.setNextRetryTime(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        outboxEventRepository.save(event);
    }

    private boolean containsAggregate(JsonNode items, String aggregateId) {
        for (JsonNode item : items) {
            if (aggregateId.equals(item.path("aggregateId").asText())) {
                return true;
            }
        }
        return false;
    }
}
