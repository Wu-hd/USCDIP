package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.repository.FeatureViewAccessAuditRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000",
        "backend.notification.retry-fixed-delay-ms=3600000",
        "backend.websocket.bridge-fixed-delay-ms=3600000",
        "backend.websocket.idle-scan-fixed-delay-ms=3600000"
})
@AutoConfigureMockMvc
class FeatureViewServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private FeatureViewAccessAuditRepository auditRepository;

    @Test
    void algorithmEngineerReceivesMaskedMetricFeaturesByDefault() throws Exception {
        TokenPairResponse algorithmToken = localTokenService.issueForUser("U-ALGO-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/feature-views/metrics")
                        .param("metricCode", "PRESSURE")
                        .param("pageSize", "5")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].featureRecordId").exists())
                .andExpect(jsonPath("$.data.items[0].maskedDeviceId").exists())
                .andExpect(jsonPath("$.data.items[0].sourceRecordId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].deviceId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].deviceName").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].geometry2d").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].coordinateBucket").exists());
    }

    @Test
    void detailWithoutGrantIsDeniedAndAudited() throws Exception {
        TokenPairResponse algorithmToken = localTokenService.issueForUser("U-ALGO-001", "127.0.0.1", "JUnit");
        long before = auditRepository.count();

        mockMvc.perform(get("/api/feature-views/metrics")
                        .param("viewLevel", "DETAIL")
                        .param("deviceId", "DEV-003")
                        .param("metricCode", "VIBRATION")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FEATURE_VIEW_DENIED"));

        Assertions.assertEquals(before + 1, auditRepository.count());
        Assertions.assertTrue(auditRepository.findAllByOrderByCreatedAtDesc().stream()
                .anyMatch(audit -> "U-ALGO-001".equals(audit.getUserId())
                        && "DENIED".equals(audit.getDecision())
                        && "DETAIL".equals(audit.getRequestedViewLevel())));
    }

    @Test
    void activeGrantAllowsDetailAndRevocationDeniesAgain() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse algorithmToken = localTokenService.issueForUser("U-ALGO-001", "127.0.0.1", "JUnit");

        MvcResult grantResult = mockMvc.perform(post("/api/feature-views/grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "U-ALGO-001",
                                "viewLevel", "DETAIL",
                                "targetType", "DEVICE",
                                "targetId", "DEV-003",
                                "reason", "B-26 integration detail troubleshooting",
                                "expiresAt", "2026-12-31T23:59:59"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();

        String grantId = objectMapper.readTree(grantResult.getResponse().getContentAsString()).path("data").path("grantId").asText();

        mockMvc.perform(get("/api/feature-views/metrics")
                        .param("viewLevel", "DETAIL")
                        .param("deviceId", "DEV-003")
                        .param("metricCode", "VIBRATION")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sourceRecordId").exists())
                .andExpect(jsonPath("$.data.items[0].deviceId").value("DEV-003"))
                .andExpect(jsonPath("$.data.items[0].deviceName").exists())
                .andExpect(jsonPath("$.data.items[0].geometry2d").exists());

        mockMvc.perform(post("/api/feature-views/grants/{grantId}/revoke", grantId)
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        mockMvc.perform(get("/api/feature-views/metrics")
                        .param("viewLevel", "DETAIL")
                        .param("deviceId", "DEV-003")
                        .param("metricCode", "VIBRATION")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FEATURE_VIEW_DENIED"));
    }

    @Test
    void modelResultFeaturesAreMaskedAndAudited() throws Exception {
        TokenPairResponse algorithmToken = localTokenService.issueForUser("U-ALGO-001", "127.0.0.1", "JUnit");

        MvcResult result = mockMvc.perform(get("/api/feature-views/model-results")
                        .param("modelCode", "LEAK_DETECTOR")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].featureResultId").exists())
                .andExpect(jsonPath("$.data.items[0].modelResultId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].segmentId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].nodeId").doesNotExist())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
        Assertions.assertFalse(items.isEmpty());
        Assertions.assertTrue(auditRepository.findAllByOrderByCreatedAtDesc().stream()
                .anyMatch(audit -> "MODEL_RESULTS".equals(audit.getQueryType())
                        && "MASKED".equals(audit.getEffectiveViewLevel())
                        && "ALLOW".equals(audit.getDecision())));
    }

    @Test
    void auditQueryRequiresModelWritePermission() throws Exception {
        TokenPairResponse algorithmToken = localTokenService.issueForUser("U-ALGO-001", "127.0.0.1", "JUnit");
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/feature-views/audits")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MENU_PERMISSION_DENIED"));

        mockMvc.perform(get("/api/feature-views/audits")
                        .param("userId", "U-ALGO-001")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value("U-ALGO-001"));
    }
}
