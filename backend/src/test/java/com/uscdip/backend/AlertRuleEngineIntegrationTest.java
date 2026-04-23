package com.uscdip.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.AlertRuleEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.repository.AlertRecordRepository;
import com.uscdip.backend.repository.AlertRuleRepository;
import com.uscdip.backend.repository.TsMetricRepository;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AlertRuleEngineIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertRecordRepository alertRecordRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private TsMetricRepository tsMetricRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("backend.oidc.enabled", () -> "true");
        registry.add("backend.oidc.issuer-uri", () -> ISSUER_URI);
        registry.add("backend.oidc.access-token-secret", () -> "test-local-access-token-secret");
    }

    @AfterAll
    static void tearDown() {
        ISSUER_SERVER.stop(0);
    }

    @Test
    void thresholdRuleHitGeneratesTriggeredAlertWithExpectedConfidence() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        long countBefore = alertRecordRepository.count();

        mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of("INGR-SEED-001"),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceMetricCount").value(1))
                .andExpect(jsonPath("$.data.evaluatedRuleCount").value(1))
                .andExpect(jsonPath("$.data.generatedAlertCount").value(1))
                .andExpect(jsonPath("$.data.alertRecords[0].decision").value("TRIGGERED"))
                .andExpect(jsonPath("$.data.alertRecords[0].alertConfRaw").value(0.92))
                .andExpect(jsonPath("$.data.alertRecords[0].alertConfFinal").value(0.88))
                .andExpect(jsonPath("$.data.alertRecords[0].metricCode").value("PRESSURE"));

        Assertions.assertEquals(countBefore + 1, alertRecordRepository.count());
    }

    @Test
    void dqScoreBandsProduceReviewRequiredAndBlockedResults() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of("INGR-SEED-011"),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.alertRecords[0].decision").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.alertRecords[0].alertConfFinal").value(0.77));

        mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of("INGR-SEED-010"),
                                "ruleCodes", List.of("ALR-TH-DQ-ANOMALY")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.alertRecords[0].decision").value("DQ_BLOCKED"))
                .andExpect(jsonPath("$.data.alertRecords[0].metricCode").value("DQ_SCORE"))
                .andExpect(jsonPath("$.data.alertRecords[0].alertConfFinal").value(0.51));
    }

    @Test
    void compositeAnyAndAllRulesAggregateConfidenceAsPlanned() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of("INGR-SEED-011"),
                                "ruleCodes", List.of("ALR-CB-PRESSURE-OR-DQ", "ALR-CB-PRESSURE-AND-DQ")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.generatedAlertCount").value(2))
                .andExpect(jsonPath("$.data.alertRecords[*].ruleCode", hasItems("ALR-CB-PRESSURE-OR-DQ", "ALR-CB-PRESSURE-AND-DQ")))
                .andExpect(jsonPath("$.data.alertRecords[?(@.ruleCode=='ALR-CB-PRESSURE-OR-DQ')].alertConfRaw").value(hasItems(0.92)))
                .andExpect(jsonPath("$.data.alertRecords[?(@.ruleCode=='ALR-CB-PRESSURE-AND-DQ')].alertConfRaw").value(hasItems(0.7)))
                .andExpect(jsonPath("$.data.alertRecords[?(@.ruleCode=='ALR-CB-PRESSURE-OR-DQ')].alertConfFinal").value(hasItems(0.77)))
                .andExpect(jsonPath("$.data.alertRecords[?(@.ruleCode=='ALR-CB-PRESSURE-AND-DQ')].alertConfFinal").value(hasItems(0.58)));
    }

    @Test
    void evaluateEndpointSupportsSourceBatchIdAndAutomaticTriggerAfterIngest() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceBatchId", "INGB-SEED-MQTT-001",
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceMetricCount").value(2))
                .andExpect(jsonPath("$.data.generatedAlertCount").value(1))
                .andExpect(jsonPath("$.data.alertRecords[0].sourceBatchId").value("INGB-SEED-MQTT-001"));

        MvcResult ingestResult = mockMvc.perform(post("/api/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "protocolType", "MQTT",
                                "sourceType", "EDGE_GATEWAY",
                                "sourceKey", "EDGE-HZ-GW-ALERT-01",
                                "traceId", "TRACE-ALERT-JUNIT-001",
                                "isBackfill", false,
                                "metrics", List.of(
                                        Map.of(
                                                "deviceId", "DEV-001",
                                                "metricCode", "pressure",
                                                "value", 0.97,
                                                "eventTime", "2026-04-25T09:00:00",
                                                "recvTime", "2026-04-25T09:00:02",
                                                "deviceTime", "2026-04-25T08:59:59"
                                        )
                                )
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();

        String batchId = objectMapper.readTree(ingestResult.getResponse().getContentAsString()).path("data").path("batchId").asText();
        List<TsMetricEntity> metrics = tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(batchId);
        Assertions.assertEquals(1, metrics.size());

        mockMvc.perform(get("/api/alerts")
                        .param("sourceBatchId", batchId)
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[*].ruleCode", hasItems("ALR-TH-PRESSURE-HIGH", "ALR-CB-PRESSURE-OR-DQ")))
                .andExpect(jsonPath("$.data.items[*].decision", hasItems("TRIGGERED")));
    }

    @Test
    void alertQueriesRespectDeviceDataScope() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/alerts")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].alertId", hasItems("ALERT-SEED-001", "ALERT-SEED-002", "ALERT-SEED-003")))
                .andExpect(jsonPath("$.data.items[*].alertId", not(hasItems("ALERT-SEED-004"))));

        mockMvc.perform(get("/api/alerts/ALERT-SEED-004")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));

        mockMvc.perform(get("/api/alerts/ALERT-SEED-004")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value("DEV-003"))
                .andExpect(jsonPath("$.data.ruleCode").value("ALR-TH-VIBRATION-HIGH"));
    }

    @Test
    void invalidAndUnknownRulesReturnPlannedErrors() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of("INGR-SEED-001"),
                                "ruleCodes", List.of("ALR-NOT-FOUND")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ALERT_RULE_NOT_FOUND"));

        String ruleId = "ARULE-JUNIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        AlertRuleEntity badRule = new AlertRuleEntity(
                ruleId,
                "ALR-BAD-JUNIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "Bad JUnit Rule",
                "THRESHOLD",
                "PRESSURE",
                null,
                0.8,
                null,
                null,
                0.5,
                "HIGH",
                true,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        alertRuleRepository.save(badRule);
        try {
            mockMvc.perform(post("/api/alerts/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "sourceRecordIds", List.of("INGR-SEED-001"),
                                    "ruleCodes", List.of(badRule.getRuleCode())
                            )))
                            .header("Authorization", "Bearer " + adminToken.accessToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("ALERT_RULE_INVALID"));
        } finally {
            alertRuleRepository.deleteById(ruleId);
        }
    }

    private static HttpServer startIssuerServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/realms/uscdip/.well-known/openid-configuration", jsonHandler(() -> """
                    {
                      "issuer":"%s",
                      "authorization_endpoint":"%s/protocol/openid-connect/auth",
                      "token_endpoint":"%s/protocol/openid-connect/token",
                      "jwks_uri":"%s/protocol/openid-connect/certs",
                      "end_session_endpoint":"%s/protocol/openid-connect/logout"
                    }
                    """.formatted(ISSUER_URI, ISSUER_URI, ISSUER_URI, ISSUER_URI, ISSUER_URI)));
            server.createContext("/realms/uscdip/protocol/openid-connect/certs", jsonHandler(() -> """
                    {"keys":[]}
                    """));
            server.createContext("/realms/uscdip/protocol/openid-connect/auth", jsonHandler(() -> "{}"));
            server.createContext("/realms/uscdip/protocol/openid-connect/token", jsonHandler(() -> "{}"));
            server.createContext("/realms/uscdip/protocol/openid-connect/logout", jsonHandler(() -> "{}"));
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start OIDC issuer stub", ex);
        }
    }

    private static HttpHandler jsonHandler(JsonSupplier jsonSupplier) {
        return exchange -> writeJson(exchange, jsonSupplier.get());
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    @FunctionalInterface
    private interface JsonSupplier {
        String get();
    }
}
