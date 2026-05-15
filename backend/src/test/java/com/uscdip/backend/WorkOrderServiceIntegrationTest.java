package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.entity.OutboxEventEntity;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.OutboxEventRepository;
import com.uscdip.backend.repository.WorkOrderRepository;
import com.uscdip.backend.service.LocalTokenService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000"
})
@AutoConfigureMockMvc
class WorkOrderServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private ObjectScopeBindingRepository objectScopeBindingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void createAllowsMultipleWorkOrdersForSameIncidentAndInheritsScope() throws Exception {
        TokenPairResponse dispatcherToken = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");

        String firstWorkOrderId = createWorkOrder(dispatcherToken, "B-22 first work order");
        String secondWorkOrderId = createWorkOrder(dispatcherToken, "B-22 second work order");

        Assertions.assertNotEquals(firstWorkOrderId, secondWorkOrderId);
        Assertions.assertEquals(2, workOrderRepository.findByIncidentId("INC-ALERT-SEED-001").stream()
                .filter(workOrder -> firstWorkOrderId.equals(workOrder.getWorkOrderId()) || secondWorkOrderId.equals(workOrder.getWorkOrderId()))
                .count());

        ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId("WORK_ORDER", firstWorkOrderId).orElseThrow();
        Assertions.assertEquals("REGION-HZ", binding.getRegionId());
        Assertions.assertEquals("U-INSPECT-001", binding.getOwnerUserId());
        Assertions.assertEquals("zhangsan", binding.getOwnerUsername());

        List<OutboxEventEntity> events = outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("WORK_ORDER", firstWorkOrderId);
        Assertions.assertTrue(events.stream().anyMatch(event -> "WORK_ORDER_CREATED".equals(event.getEventType())));
        assertTraceLinked(events);
    }

    @Test
    void dispatchAcceptCompleteCloseAndWritebackFollowStateMachine() throws Exception {
        TokenPairResponse dispatcherToken = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");
        TokenPairResponse inspectorToken = localTokenService.issueForUser("U-INSPECT-001", "127.0.0.1", "JUnit");
        String workOrderId = createWorkOrder(dispatcherToken, "B-22 lifecycle work order");

        mockMvc.perform(post("/api/workorders/{workOrderId}/dispatch", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assigneeUserId", "U-INSPECT-001",
                                "assignee", "zhangsan",
                                "slaDueAt", "2026-04-25T18:00:00",
                                "reason", "dispatch for inspection"
                        )))
                        .header("Authorization", "Bearer " + dispatcherToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.assigneeUserId").value("U-INSPECT-001"));

        mockMvc.perform(post("/api/workorders/{workOrderId}/accept", workOrderId)
                        .header("Authorization", "Bearer " + inspectorToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.acceptedBy").value("U-INSPECT-001"));

        mockMvc.perform(post("/api/workorders/{workOrderId}/complete", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("completionSummary", "现场处置完成")))
                        .header("Authorization", "Bearer " + inspectorToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completionSummary").value("现场处置完成"));

        mockMvc.perform(post("/api/workorders/{workOrderId}/writeback", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "writebackType", "FALSE_POSITIVE",
                                "writebackReason", "现场复核为误报"
                        )))
                        .header("Authorization", "Bearer " + dispatcherToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.writebackType").value("FALSE_POSITIVE"));

        mockMvc.perform(post("/api/workorders/{workOrderId}/close", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("closeReason", "验收关闭")))
                        .header("Authorization", "Bearer " + dispatcherToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closeReason").value("验收关闭"));

        List<OutboxEventEntity> events = outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("WORK_ORDER", workOrderId);
        List<String> eventTypes = events.stream().map(OutboxEventEntity::getEventType).toList();
        Assertions.assertTrue(eventTypes.contains("WORK_ORDER_DISPATCHED"));
        Assertions.assertTrue(eventTypes.contains("WORK_ORDER_ACCEPTED"));
        Assertions.assertTrue(eventTypes.contains("WORK_ORDER_COMPLETED"));
        Assertions.assertTrue(eventTypes.contains("WORK_ORDER_WRITEBACK_RECORDED"));
        Assertions.assertTrue(eventTypes.contains("WORK_ORDER_CLOSED"));
        assertTraceLinked(events);
    }

    @Test
    void transferUpdatesAssigneeScope() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/workorders/WO-B22-DISPATCHED-001/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assignee", "lisi",
                                "slaDueAt", "2026-04-26T18:00:00",
                                "reason", "transfer to another inspector"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.assignee").value("lisi"));

        ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId("WORK_ORDER", "WO-B22-DISPATCHED-001").orElseThrow();
        Assertions.assertNull(binding.getOwnerUserId());
        Assertions.assertEquals("lisi", binding.getOwnerUsername());
    }

    @Test
    void invalidTransitionReturnsWorkOrderInvalidState() throws Exception {
        TokenPairResponse inspectorToken = localTokenService.issueForUser("U-INSPECT-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/workorders/WO-B22-CREATED-001/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("completionSummary", "cannot complete created order")))
                        .header("Authorization", "Bearer " + inspectorToken.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORK_ORDER_INVALID_STATE"));
    }

    @Test
    void workOrderQueriesRespectRegionAndAssigneeScope() throws Exception {
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");
        TokenPairResponse inspectorToken = localTokenService.issueForUser("U-INSPECT-001", "127.0.0.1", "JUnit");

        MvcResult regionalResult = mockMvc.perform(get("/api/workorders")
                        .param("page", "1")
                        .param("pageSize", "100")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode regionalItems = objectMapper.readTree(regionalResult.getResponse().getContentAsString()).path("data").path("items");
        Assertions.assertTrue(containsWorkOrder(regionalItems, "WO-B22-CREATED-001"));
        Assertions.assertFalse(containsWorkOrder(regionalItems, "WO-B22-ACCEPTED-001"));

        MvcResult inspectorResult = mockMvc.perform(get("/api/workorders")
                        .param("page", "1")
                        .param("pageSize", "100")
                        .header("Authorization", "Bearer " + inspectorToken.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode inspectorItems = objectMapper.readTree(inspectorResult.getResponse().getContentAsString()).path("data").path("items");
        Assertions.assertTrue(containsWorkOrder(inspectorItems, "WO-B22-CREATED-001"));
        Assertions.assertFalse(containsWorkOrder(inspectorItems, "WO-B22-ACCEPTED-001"));
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
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("workOrderId").asText();
    }

    private boolean containsWorkOrder(JsonNode items, String workOrderId) {
        for (JsonNode item : items) {
            if (workOrderId.equals(item.path("workOrderId").asText())) {
                return true;
            }
        }
        return false;
    }

    private void assertTraceLinked(List<OutboxEventEntity> events) throws Exception {
        for (OutboxEventEntity event : events) {
            Assertions.assertNotNull(event.getTraceId());
            Assertions.assertFalse(event.getTraceId().isBlank());
            JsonNode payload = objectMapper.readTree(event.getPayload());
            Assertions.assertEquals(event.getTraceId(), payload.path("traceId").asText());
        }
    }
}
