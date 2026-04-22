package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.TsMetricEntity;
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
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DataQualityIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private TsMetricRepository tsMetricRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
    void ingestMetricsAutoScoresBatchAndDetectsFlatline() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        MvcResult result = mockMvc.perform(post("/api/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "protocolType", "MQTT",
                                "sourceType", "EDGE_GATEWAY",
                                "sourceKey", "EDGE-HZ-GW-DQ-01",
                                "traceId", "TRACE-DQ-JUNIT-001",
                                "isBackfill", false,
                                "metrics", List.of(
                                        metric("DEV-001", "pressure", 0.77, "2026-04-24T09:00:00", "2026-04-24T09:00:01", "2026-04-24T08:59:59"),
                                        metric("DEV-001", "pressure", 0.77, "2026-04-24T09:00:05", "2026-04-24T09:00:06", "2026-04-24T09:00:04"),
                                        metric("DEV-001", "pressure", 0.77, "2026-04-24T09:00:10", "2026-04-24T09:00:11", "2026-04-24T09:00:09"),
                                        metric("DEV-001", "pressure", 0.77, "2026-04-24T09:00:15", "2026-04-24T09:00:16", "2026-04-24T09:00:14"),
                                        metric("DEV-001", "pressure", 0.77, "2026-04-24T09:00:20", "2026-04-24T09:00:21", "2026-04-24T09:00:19")
                                )
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dqScore.avgDqScore").exists())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String batchId = response.path("data").path("batchId").asText();
        List<TsMetricEntity> metrics = tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(batchId);

        Assertions.assertEquals(5, metrics.size());
        Assertions.assertTrue(metrics.get(4).getDqFlags().contains("STABILITY_FLATLINE"));
        Assertions.assertNotNull(metrics.get(4).getDqScore());
        Assertions.assertNotNull(metrics.get(4).getDqAlarmConfFactor());
    }

    @Test
    void backfillAndOutOfRangeWritesCarryExpectedFlags() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        MvcResult backfillResult = mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "protocolType", "MQTT",
                                "sourceType", "EDGE_GATEWAY",
                                "sourceKey", "EDGE-HZ-GW-BACKFILL-01",
                                "traceId", "TRACE-DQ-JUNIT-002",
                                "batchNo", "BATCH-DQ-001",
                                "seqNo", 1,
                                "originalSampleTime", "2026-04-20T09:00:00",
                                "isBackfill", true,
                                "metrics", List.of(
                                        metric("DEV-001", "pressure", 0.91, "2026-04-20T09:00:00", "2026-04-24T09:00:00", "2026-04-20T08:59:59")
                                )
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dqScore.lowestDqScore").exists())
                .andReturn();

        String backfillBatchId = objectMapper.readTree(backfillResult.getResponse().getContentAsString()).path("data").path("batchId").asText();
        TsMetricEntity backfillMetric = tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(backfillBatchId).get(0);
        Assertions.assertTrue(backfillMetric.getDqFlags().contains("BACKFILL_DATA"));
        Assertions.assertTrue(backfillMetric.getDqFlags().contains("TIMELINESS_DELAYED"));

        MvcResult invalidResult = mockMvc.perform(post("/api/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "protocolType", "MQTT",
                                "sourceType", "EDGE_GATEWAY",
                                "sourceKey", "EDGE-HZ-GW-DQ-02",
                                "traceId", "TRACE-DQ-JUNIT-003",
                                "isBackfill", false,
                                "metrics", List.of(
                                        metric("DEV-001", "pressure", 9.9, "2026-04-24T10:00:00", "2026-04-24T10:12:00", "2026-04-24T09:59:58")
                                )
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andReturn();

        String invalidBatchId = objectMapper.readTree(invalidResult.getResponse().getContentAsString()).path("data").path("batchId").asText();
        TsMetricEntity invalidMetric = tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(invalidBatchId).get(0);
        Assertions.assertEquals("D", invalidMetric.getDqLevel());
        Assertions.assertTrue(invalidMetric.getDqFlags().contains("VALIDITY_RANGE_VIOLATION"));
        Assertions.assertTrue(invalidMetric.getDqFlags().contains("TIMELINESS_DELAYED"));
    }

    @Test
    void scoreQueriesRespectFiltersAndDataScope() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/dq/scores")
                        .param("dqLevel", "D")
                        .param("deviceId", "DEV-001")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[*].sourceRecordId", hasItems("INGR-SEED-010")))
                .andExpect(jsonPath("$.data.items[*].sourceRecordId", not(hasItems("INGR-SEED-004"))));

        mockMvc.perform(get("/api/dq/scores/INGR-SEED-004")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));

        mockMvc.perform(get("/api/dq/scores/INGR-SEED-004")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dqLevel").value("C"))
                .andExpect(jsonPath("$.data.dqFlags").value(org.hamcrest.Matchers.containsString("BACKFILL_DATA")));

        mockMvc.perform(get("/api/dq/scores/INGR-SEED-009")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dqFlags").value(org.hamcrest.Matchers.containsString("STABILITY_FLATLINE")));
    }

    @Test
    void invalidDqQueryReturnsPlannedErrorCode() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/dq/scores")
                        .param("dqLevel", "Z")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DQ_QUERY_INVALID"));

        mockMvc.perform(get("/api/dq/scores")
                        .param("minScore", "90")
                        .param("maxScore", "20")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DQ_QUERY_INVALID"));
    }

    private Map<String, Object> metric(String deviceId, String metricCode, double value, String eventTime, String recvTime, String deviceTime) {
        return Map.of(
                "deviceId", deviceId,
                "metricCode", metricCode,
                "value", value,
                "eventTime", eventTime,
                "recvTime", recvTime,
                "deviceTime", deviceTime
        );
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
