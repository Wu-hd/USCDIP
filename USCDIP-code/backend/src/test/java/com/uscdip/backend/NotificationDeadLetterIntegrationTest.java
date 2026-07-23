package com.uscdip.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.NotificationMessageEntity;
import com.uscdip.backend.repository.DeadLetterRepository;
import com.uscdip.backend.repository.NotificationDeliveryRepository;
import com.uscdip.backend.repository.NotificationMessageRepository;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000",
        "backend.notification.retry-fixed-delay-ms=3600000",
        "backend.notification.channels=SMS",
        "backend.notification.fail-channels=SMS_ALWAYS",
        "backend.notification.max-attempts=1"
})
@AutoConfigureMockMvc
class NotificationDeadLetterIntegrationTest {

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
    private DeadLetterRepository deadLetterRepository;

    @Test
    void failedChannelExceededAttemptsEntersDeadLetter() throws Exception {
        TokenPairResponse dispatcherToken = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");
        String workOrderId = createWorkOrder(dispatcherToken);

        mockMvc.perform(post("/api/notifications/consume-outbox")
                        .header("Authorization", "Bearer " + dispatcherToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consumedNotifications").value(1));

        NotificationMessageEntity message = notificationMessageRepository.findAllByOrderByCreatedAtDescNotificationIdDesc().stream()
                .filter(item -> workOrderId.equals(item.getAggregateId()))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals("DEAD", message.getStatus());
        Assertions.assertEquals("DEAD", notificationDeliveryRepository.findByNotificationIdOrderByChannelAsc(message.getNotificationId()).get(0).getStatus());
        Assertions.assertTrue(deadLetterRepository.findByIdempotentKeyOrderByCreatedAtAsc(message.getIdempotentKey()).stream()
                .anyMatch(deadLetter -> "SEND_NOTIFICATION".equals(deadLetter.getActionType())
                        && message.getTraceId().equals(deadLetter.getTraceId())));
    }

    private String createWorkOrder(TokenPairResponse token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workorders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "incidentId", "INC-ALERT-SEED-001",
                                "description", "B-23 dead letter notification",
                                "assigneeUserId", "U-INSPECT-001",
                                "assignee", "zhangsan"
                        )))
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("workOrderId").asText();
    }
}
