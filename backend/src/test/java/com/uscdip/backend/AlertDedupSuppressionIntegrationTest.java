package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.AlertCaseEntity;
import com.uscdip.backend.entity.AlertPolicyEntity;
import com.uscdip.backend.entity.AlertRuleEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.repository.AlertCaseRepository;
import com.uscdip.backend.repository.AlertPolicyRepository;
import com.uscdip.backend.repository.AlertRuleRepository;
import com.uscdip.backend.repository.TsMetricRepository;
import com.uscdip.backend.service.AlertCaseLifecycleService;
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
class AlertDedupSuppressionIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TsMetricRepository tsMetricRepository;

    @Autowired
    private AlertCaseRepository alertCaseRepository;

    @Autowired
    private AlertPolicyRepository alertPolicyRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertCaseLifecycleService alertCaseLifecycleService;

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
    void sameDeviceAndRuleWithinWindowAreDedupedIntoOneEscalatedCase() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String suffix = uniqueSuffix();

        TsMetricEntity metric1 = saveMetric("INGR-B18-" + suffix + "-01", "INGB-B18-" + suffix + "-01", "DEV-001", "PRESSURE", "0.97",
                LocalDateTime.parse("2026-04-26T10:00:00"), 92.0, "TRACE-B18-" + suffix);
        TsMetricEntity metric2 = saveMetric("INGR-B18-" + suffix + "-02", "INGB-B18-" + suffix + "-02", "DEV-001", "PRESSURE", "0.98",
                LocalDateTime.parse("2026-04-26T10:00:05"), 92.0, "TRACE-B18-" + suffix);
        TsMetricEntity metric3 = saveMetric("INGR-B18-" + suffix + "-03", "INGB-B18-" + suffix + "-03", "DEV-001", "PRESSURE", "0.99",
                LocalDateTime.parse("2026-04-26T10:00:10"), 92.0, "TRACE-B18-" + suffix);

        MvcResult result = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(metric1.getSourceRecordId(), metric2.getSourceRecordId(), metric3.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openedCaseCount").value(1))
                .andExpect(jsonPath("$.data.dedupedCount").value(2))
                .andExpect(jsonPath("$.data.suppressedCount").value(0))
                .andExpect(jsonPath("$.data.escalatedCount").value(1))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("alertRecords");
        String caseId = root.get(0).path("caseId").asText();
        Assertions.assertEquals(caseId, root.get(1).path("caseId").asText());
        Assertions.assertEquals(caseId, root.get(2).path("caseId").asText());
        Assertions.assertEquals("ESCALATED", root.get(2).path("processStatus").asText());
        Assertions.assertEquals(1, root.get(2).path("escalationLevel").asInt());

        AlertCaseEntity caseEntity = alertCaseRepository.findById(caseId).orElseThrow();
        Assertions.assertEquals("ESCALATED", caseEntity.getCaseStatus());
        Assertions.assertEquals(3, caseEntity.getHitCount());
        Assertions.assertEquals(3, caseEntity.getUnsuppressedHitCount());
        Assertions.assertEquals(1, caseEntity.getEscalationLevel());
        Assertions.assertEquals("CRITICAL", caseEntity.getSeverityCurrent());
    }

    @Test
    void suppressionWindowMarksFollowupSuppressedAndDifferentDeviceDoesNotMerge() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String suffix = uniqueSuffix();

        TsMetricEntity metric1 = saveMetric("INGR-B18-" + suffix + "-11", "INGB-B18-" + suffix + "-11", "DEV-001", "PRESSURE", "0.96",
                LocalDateTime.parse("2026-04-26T11:00:00"), 92.0, "TRACE-B18-" + suffix);
        TsMetricEntity metric2 = saveMetric("INGR-B18-" + suffix + "-12", "INGB-B18-" + suffix + "-12", "DEV-001", "PRESSURE", "0.96",
                LocalDateTime.parse("2026-04-26T11:00:01"), 92.0, "TRACE-B18-" + suffix);
        TsMetricEntity metric3 = saveMetric("INGR-B18-" + suffix + "-13", "INGB-B18-" + suffix + "-13", "DEV-002", "PRESSURE", "0.96",
                LocalDateTime.parse("2026-04-26T11:00:00"), 92.0, "TRACE-B18-" + suffix);

        MvcResult suppressedResult = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(metric1.getSourceRecordId(), metric2.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-CB-PRESSURE-OR-DQ")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openedCaseCount").value(1))
                .andExpect(jsonPath("$.data.dedupedCount").value(0))
                .andExpect(jsonPath("$.data.suppressedCount").value(1))
                .andExpect(jsonPath("$.data.alertRecords[1].suppressed").value(true))
                .andExpect(jsonPath("$.data.alertRecords[1].processStatus").value("SUPPRESSED"))
                .andReturn();

        JsonNode suppressedRecords = objectMapper.readTree(suppressedResult.getResponse().getContentAsString()).path("data").path("alertRecords");
        String suppressedCaseId = suppressedRecords.get(0).path("caseId").asText();
        AlertCaseEntity suppressedCase = alertCaseRepository.findById(suppressedCaseId).orElseThrow();
        Assertions.assertEquals("SUPPRESSED", suppressedCase.getCaseStatus());
        Assertions.assertEquals(2, suppressedCase.getHitCount());
        Assertions.assertEquals(1, suppressedCase.getUnsuppressedHitCount());
        Assertions.assertEquals(LocalDateTime.parse("2026-04-26T11:00:01"), suppressedCase.getLastTriggeredAt());

        MvcResult otherDeviceResult = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(metric3.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-CB-PRESSURE-OR-DQ")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openedCaseCount").value(1))
                .andReturn();

        String otherDeviceCaseId = objectMapper.readTree(otherDeviceResult.getResponse().getContentAsString())
                .path("data").path("alertRecords").get(0).path("caseId").asText();
        Assertions.assertNotEquals(suppressedCaseId, otherDeviceCaseId);
    }

    @Test
    void dqBandsRecoveryAndCaseQueriesBehaveAsPlanned() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");
        String suffix = uniqueSuffix();

        TsMetricEntity dq1 = saveMetric("INGR-B18-" + suffix + "-21", "INGB-B18-" + suffix + "-21", "DEV-001", "PRESSURE", "0.98",
                LocalDateTime.parse("2026-04-26T12:00:00"), 75.0, "TRACE-B18-" + suffix);
        TsMetricEntity dq2 = saveMetric("INGR-B18-" + suffix + "-22", "INGB-B18-" + suffix + "-22", "DEV-001", "PRESSURE", "0.98",
                LocalDateTime.parse("2026-04-26T12:00:05"), 75.0, "TRACE-B18-" + suffix);
        TsMetricEntity dq3 = saveMetric("INGR-B18-" + suffix + "-23", "INGB-B18-" + suffix + "-23", "DEV-001", "PRESSURE", "0.98",
                LocalDateTime.parse("2026-04-26T12:00:10"), 75.0, "TRACE-B18-" + suffix);
        TsMetricEntity dq4 = saveMetric("INGR-B18-" + suffix + "-24", "INGB-B18-" + suffix + "-24", "DEV-001", "PRESSURE", "0.98",
                LocalDateTime.parse("2026-04-26T12:00:15"), 75.0, "TRACE-B18-" + suffix);
        TsMetricEntity dq5 = saveMetric("INGR-B18-" + suffix + "-25", "INGB-B18-" + suffix + "-25", "DEV-001", "PRESSURE", "0.98",
                LocalDateTime.parse("2026-04-26T12:00:20"), 75.0, "TRACE-B18-" + suffix);

        MvcResult beforeEscalation = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(dq1.getSourceRecordId(), dq2.getSourceRecordId(), dq3.getSourceRecordId(), dq4.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.escalatedCount").value(0))
                .andReturn();

        String dqCaseId = objectMapper.readTree(beforeEscalation.getResponse().getContentAsString())
                .path("data").path("alertRecords").get(0).path("caseId").asText();
        AlertCaseEntity beforeEscalatedCase = alertCaseRepository.findById(dqCaseId).orElseThrow();
        Assertions.assertEquals("ACTIVE", beforeEscalatedCase.getCaseStatus());
        Assertions.assertEquals(4, beforeEscalatedCase.getUnsuppressedHitCount());
        Assertions.assertEquals(0, beforeEscalatedCase.getEscalationLevel());

        mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(dq5.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.escalatedCount").value(1))
                .andExpect(jsonPath("$.data.alertRecords[0].escalationLevel").value(1));

        AlertCaseEntity afterEscalatedCase = alertCaseRepository.findById(dqCaseId).orElseThrow();
        Assertions.assertEquals("ESCALATED", afterEscalatedCase.getCaseStatus());
        Assertions.assertEquals(5, afterEscalatedCase.getUnsuppressedHitCount());
        Assertions.assertEquals(1, afterEscalatedCase.getEscalationLevel());

        TsMetricEntity reviewMetric = saveMetric("INGR-B18-" + suffix + "-26", "INGB-B18-" + suffix + "-26", "DEV-001", "PRESSURE", "0.97",
                LocalDateTime.parse("2026-04-26T12:10:00"), 66.0, "TRACE-B18-" + suffix);
        MvcResult reviewResult = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(reviewMetric.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.alertRecords[0].decision").value("REVIEW_REQUIRED"))
                .andReturn();
        String reviewCaseId = objectMapper.readTree(reviewResult.getResponse().getContentAsString())
                .path("data").path("alertRecords").get(0).path("caseId").asText();
        Assertions.assertEquals("REVIEW_ONLY", alertCaseRepository.findById(reviewCaseId).orElseThrow().getCaseStatus());

        TsMetricEntity blockedMetric = saveMetric("INGR-B18-" + suffix + "-27", "INGB-B18-" + suffix + "-27", "DEV-001", "PRESSURE", "0.97",
                LocalDateTime.parse("2026-04-26T12:20:00"), 45.0, "TRACE-B18-" + suffix);
        MvcResult blockedResult = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(blockedMetric.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.alertRecords[0].decision").value("DQ_BLOCKED"))
                .andReturn();
        String blockedCaseId = objectMapper.readTree(blockedResult.getResponse().getContentAsString())
                .path("data").path("alertRecords").get(0).path("caseId").asText();
        Assertions.assertEquals("DQ_BLOCKED", alertCaseRepository.findById(blockedCaseId).orElseThrow().getCaseStatus());

        TsMetricEntity staleMetric = saveMetric("INGR-B18-" + suffix + "-28", "INGB-B18-" + suffix + "-28", "DEV-001", "PRESSURE", "0.97",
                LocalDateTime.parse("2026-04-01T08:00:00"), 92.0, "TRACE-B18-" + suffix);
        MvcResult staleResult = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(staleMetric.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();
        String staleCaseId = objectMapper.readTree(staleResult.getResponse().getContentAsString())
                .path("data").path("alertRecords").get(0).path("caseId").asText();
        int recoveredCount = alertCaseLifecycleService.recoverExpiredCasesNow();
        Assertions.assertTrue(recoveredCount >= 1);
        Assertions.assertEquals("RECOVERED", alertCaseRepository.findById(staleCaseId).orElseThrow().getCaseStatus());

        mockMvc.perform(get("/api/alerts/cases")
                        .param("page", "1")
                        .param("pageSize", "50")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].caseId", hasItems("ALCASE-SEED-001", "ALCASE-SEED-005")))
                .andExpect(jsonPath("$.data.items[*].caseId", not(hasItems("ALCASE-SEED-007"))));

        mockMvc.perform(get("/api/alerts/cases/ALCASE-SEED-007")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));

        mockMvc.perform(get("/api/alerts/cases/ALCASE-SEED-007")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caseStatus").value("REVIEW_ONLY"));

        mockMvc.perform(get("/api/alerts/policies")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].ruleCode", hasItems("ALR-TH-PRESSURE-HIGH", "ALR-CB-PRESSURE-OR-DQ")));
    }

    @Test
    void missingAndInvalidPoliciesReturnPlannedErrors() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String suffix = uniqueSuffix();

        TsMetricEntity metric = saveMetric("INGR-B18-" + suffix + "-31", "INGB-B18-" + suffix + "-31", "DEV-001", "PRESSURE", "0.97",
                LocalDateTime.parse("2026-04-26T13:00:00"), 92.0, "TRACE-B18-" + suffix);

        String missingRuleId = "ARULE-B18-MISSING-" + suffix;
        String missingRuleCode = "ALR-B18-NO-POLICY-" + suffix;
        alertRuleRepository.save(new AlertRuleEntity(
                missingRuleId,
                missingRuleCode,
                "Missing Policy Rule",
                "THRESHOLD",
                "PRESSURE",
                "GT",
                0.85,
                null,
                null,
                0.8,
                "HIGH",
                true,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));

        mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(metric.getSourceRecordId()),
                                "ruleCodes", List.of(missingRuleCode)
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ALERT_POLICY_NOT_FOUND"));

        String invalidRuleId = "ARULE-B18-INVALID-" + suffix;
        String invalidRuleCode = "ALR-B18-BAD-POLICY-" + suffix;
        String invalidPolicyId = "APOL-B18-INVALID-" + suffix;
        alertRuleRepository.save(new AlertRuleEntity(
                invalidRuleId,
                invalidRuleCode,
                "Invalid Policy Rule",
                "THRESHOLD",
                "PRESSURE",
                "GT",
                0.85,
                null,
                null,
                0.8,
                "HIGH",
                true,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
        alertPolicyRepository.save(new AlertPolicyEntity(
                invalidPolicyId,
                invalidRuleCode,
                600,
                2,
                600,
                5,
                3,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));

        try {
            mockMvc.perform(post("/api/alerts/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "sourceRecordIds", List.of(metric.getSourceRecordId()),
                                    "ruleCodes", List.of(invalidRuleCode)
                            )))
                            .header("Authorization", "Bearer " + adminToken.accessToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("ALERT_POLICY_INVALID"));
        } finally {
            alertPolicyRepository.deleteById(invalidPolicyId);
            alertRuleRepository.deleteById(invalidRuleId);
            alertRuleRepository.deleteById(missingRuleId);
        }
    }

    private TsMetricEntity saveMetric(
            String recordId,
            String batchId,
            String deviceId,
            String metricCode,
            String metricValue,
            LocalDateTime eventTime,
            double dqScore,
            String traceId
    ) {
        double factor = Math.round((0.5d + (0.5d * dqScore / 100.0d)) * 100.0d) / 100.0d;
        TsMetricEntity metric = new TsMetricEntity(
                recordId,
                deviceId,
                metricCode,
                metricValue,
                eventTime,
                eventTime.plusSeconds(1),
                eventTime.minusSeconds(1),
                traceId,
                false,
                batchId,
                null,
                null,
                null,
                false,
                1000L,
                LocalDateTime.now(),
                dqScore,
                dqLevel(dqScore),
                null,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                factor,
                LocalDateTime.now()
        );
        return tsMetricRepository.save(metric);
    }

    private String dqLevel(double dqScore) {
        if (dqScore >= 85.0d) {
            return "A";
        }
        if (dqScore >= 70.0d) {
            return "B";
        }
        if (dqScore >= 60.0d) {
            return "C";
        }
        return "D";
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
