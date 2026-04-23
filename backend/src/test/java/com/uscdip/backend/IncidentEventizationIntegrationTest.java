package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.AlertCaseEntity;
import com.uscdip.backend.entity.IncidentEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.repository.AlertCaseRepository;
import com.uscdip.backend.repository.IncidentRepository;
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
class IncidentEventizationIntegrationTest {

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
    private IncidentRepository incidentRepository;

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
    void triggeredAndReviewCasesEventizeAsPlannedAndDeduplicateIncidentByCase() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String suffix = uniqueSuffix();

        TsMetricEntity openMetric1 = saveMetric("INGR-B19-" + suffix + "-01", "INGB-B19-" + suffix + "-01", "DEV-001", "PRESSURE", "0.97",
                LocalDateTime.parse("2026-04-27T09:00:00"), 92.0, "TRACE-B19-" + suffix);
        TsMetricEntity openMetric2 = saveMetric("INGR-B19-" + suffix + "-02", "INGB-B19-" + suffix + "-02", "DEV-001", "PRESSURE", "0.98",
                LocalDateTime.parse("2026-04-27T09:00:05"), 92.0, "TRACE-B19-" + suffix);
        TsMetricEntity reviewMetric = saveMetric("INGR-B19-" + suffix + "-03", "INGB-B19-" + suffix + "-03", "DEV-001", "PRESSURE", "0.96",
                LocalDateTime.parse("2026-04-27T09:10:00"), 66.0, "TRACE-B19-" + suffix);

        MvcResult openResult = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(openMetric1.getSourceRecordId(), openMetric2.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode openRecords = objectMapper.readTree(openResult.getResponse().getContentAsString()).path("data").path("alertRecords");
        String openCaseId = openRecords.get(0).path("caseId").asText();
        IncidentEntity openIncident = incidentRepository.findBySourceCaseId(openCaseId).orElseThrow();
        Assertions.assertEquals("OPEN", openIncident.getStatus());
        Assertions.assertEquals("ALERT_EVENT", openIncident.getIncidentType());
        Assertions.assertEquals(openRecords.get(1).path("alertId").asText(), openIncident.getSourceAlertId());
        Assertions.assertEquals(1L, incidentRepository.findAll().stream()
                .filter(incident -> openCaseId.equals(incident.getSourceCaseId()))
                .count());

        MvcResult reviewResult = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(reviewMetric.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();

        String reviewCaseId = objectMapper.readTree(reviewResult.getResponse().getContentAsString())
                .path("data").path("alertRecords").get(0).path("caseId").asText();
        IncidentEntity reviewIncident = incidentRepository.findBySourceCaseId(reviewCaseId).orElseThrow();
        Assertions.assertEquals("PENDING_CONFIRMATION", reviewIncident.getStatus());
    }

    @Test
    void dqBlockedDoesNotCreateFormalIncidentAndRecoveredCaseResolvesIncident() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String suffix = uniqueSuffix();

        TsMetricEntity blockedMetric = saveMetric("INGR-B19-" + suffix + "-11", "INGB-B19-" + suffix + "-11", "DEV-001", "PRESSURE", "0.97",
                LocalDateTime.parse("2026-04-27T10:00:00"), 45.0, "TRACE-B19-" + suffix);
        MvcResult blockedResult = mockMvc.perform(post("/api/alerts/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceRecordIds", List.of(blockedMetric.getSourceRecordId()),
                                "ruleCodes", List.of("ALR-TH-PRESSURE-HIGH")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();

        String blockedCaseId = objectMapper.readTree(blockedResult.getResponse().getContentAsString())
                .path("data").path("alertRecords").get(0).path("caseId").asText();
        Assertions.assertTrue(incidentRepository.findBySourceCaseId(blockedCaseId).isEmpty());

        TsMetricEntity staleMetric = saveMetric("INGR-B19-" + suffix + "-12", "INGB-B19-" + suffix + "-12", "DEV-001", "PRESSURE", "0.97",
                LocalDateTime.parse("2026-04-01T08:00:00"), 92.0, "TRACE-B19-" + suffix);
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
        IncidentEntity staleIncidentBefore = incidentRepository.findBySourceCaseId(staleCaseId).orElseThrow();
        Assertions.assertEquals("OPEN", staleIncidentBefore.getStatus());

        int recoveredCount = alertCaseLifecycleService.recoverExpiredCasesNow();
        Assertions.assertTrue(recoveredCount >= 1);

        AlertCaseEntity recoveredCase = alertCaseRepository.findById(staleCaseId).orElseThrow();
        IncidentEntity staleIncidentAfter = incidentRepository.findBySourceCaseId(staleCaseId).orElseThrow();
        Assertions.assertEquals("RECOVERED", recoveredCase.getCaseStatus());
        Assertions.assertEquals("RESOLVED", staleIncidentAfter.getStatus());
        Assertions.assertEquals("AUTO_RECOVERED", staleIncidentAfter.getCloseReason());
        Assertions.assertNotNull(staleIncidentAfter.getResolvedAt());
    }

    @Test
    void incidentApiSupportsQueryScopeAndManualConfirm() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/incidents")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].incidentId", hasItems("INC-001", "INC-ALERT-SEED-001", "INC-ALERT-SEED-003", "INC-ALERT-SEED-004", "INC-CAL-SEED-001", "INC-CAL-SEED-002")))
                .andExpect(jsonPath("$.data.items[*].incidentId", not(hasItems("INC-002", "INC-ALERT-SEED-002"))));

        mockMvc.perform(get("/api/incidents/INC-ALERT-SEED-002")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));

        mockMvc.perform(post("/api/incidents/INC-ALERT-SEED-002/confirm")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.confirmedBy").value("U-ADMIN-001"))
                .andExpect(jsonPath("$.data.confirmedAt").exists());

        mockMvc.perform(post("/api/incidents/INC-ALERT-SEED-001/confirm")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INCIDENT_INVALID_STATE"));
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
