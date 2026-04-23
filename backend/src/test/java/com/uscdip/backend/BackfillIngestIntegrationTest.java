package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.IngestBatchEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.entity.TsWriteLogEntity;
import com.uscdip.backend.repository.IngestBatchRepository;
import com.uscdip.backend.repository.TsMetricRepository;
import com.uscdip.backend.repository.TsWriteLogRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackfillIngestIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IngestBatchRepository ingestBatchRepository;

    @Autowired
    private TsMetricRepository tsMetricRepository;

    @Autowired
    private TsWriteLogRepository tsWriteLogRepository;

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
    void firstBackfillPersistsBatchMetricAndWriteLog() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        MvcResult result = mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backfillRequest(
                                "BATCH-B16-VALID-001",
                                1,
                                0.91,
                                "2026-04-20T09:00:00",
                                "2026-04-24T09:00:00",
                                "2026-04-20T08:59:59",
                                "2026-04-20T09:00:00"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isBackfill").value(true))
                .andExpect(jsonPath("$.data.tsdbWrite.writeStatus").value("SUCCESS"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String batchId = response.path("batchId").asText();
        String writeLogId = response.path("tsdbWrite").path("writeLogId").asText();

        IngestBatchEntity batch = ingestBatchRepository.findById(batchId).orElseThrow();
        List<TsMetricEntity> metrics = tsMetricRepository.findBySourceBatchIdOrderByEventTimeAsc(batchId);
        TsWriteLogEntity writeLog = tsWriteLogRepository.findById(writeLogId).orElseThrow();

        Assertions.assertEquals("BATCH-B16-VALID-001", batch.getBatchNo());
        Assertions.assertEquals(1L, batch.getSeqNo());
        Assertions.assertEquals(LocalDateTime.parse("2026-04-20T09:00:00"), batch.getOriginalSampleTime());
        Assertions.assertEquals(1, metrics.size());
        Assertions.assertEquals("BATCH-B16-VALID-001", metrics.get(0).getBatchNo());
        Assertions.assertEquals(1L, metrics.get(0).getSeqNo());
        Assertions.assertEquals("BACKFILL", writeLog.getWriteType());
        Assertions.assertEquals("SUCCESS", writeLog.getStatus());
    }

    @Test
    void identicalReplayReturnsCreatedButDoesNotDuplicateTsMetric() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        Map<String, Object> request = backfillRequest(
                "BATCH-B16-IDEMPOTENT-001",
                1,
                0.95,
                "2026-04-21T08:30:00",
                "2026-04-24T08:30:00",
                "2026-04-21T08:29:58",
                "2026-04-21T08:30:00"
        );

        mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tsdbWrite.writeStatus").value("SUCCESS"));

        mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tsdbWrite.writeStatus").value("SUCCESS"));

        Assertions.assertEquals(
                1,
                tsMetricRepository.countByDeviceIdAndMetricCodeAndBatchNoAndSeqNo("DEV-001", "PRESSURE", "BATCH-B16-IDEMPOTENT-001", 1L)
        );
    }

    @Test
    void conflictingReplayReturnsBackfillDuplicate() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backfillRequest(
                                "BATCH-B16-CONFLICT-001",
                                1,
                                0.98,
                                "2026-04-21T09:00:00",
                                "2026-04-24T09:00:00",
                                "2026-04-21T08:59:58",
                                "2026-04-21T09:00:00"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backfillRequest(
                                "BATCH-B16-CONFLICT-001",
                                1,
                                1.28,
                                "2026-04-21T09:00:00",
                                "2026-04-24T09:00:00",
                                "2026-04-21T08:59:58",
                                "2026-04-21T09:00:00"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BACKFILL_DUPLICATE"));

        Assertions.assertEquals(
                1,
                tsMetricRepository.countByDeviceIdAndMetricCodeAndBatchNoAndSeqNo("DEV-001", "PRESSURE", "BATCH-B16-CONFLICT-001", 1L)
        );
    }

    @Test
    void outOfOrderSeqNoIsAccepted() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backfillRequest(
                                "BATCH-B16-ORDER-001",
                                2,
                                0.82,
                                "2026-04-22T10:05:00",
                                "2026-04-24T10:05:00",
                                "2026-04-22T10:04:58",
                                "2026-04-22T10:05:00"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tsdbWrite.writeStatus").value("SUCCESS"));

        mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backfillRequest(
                                "BATCH-B16-ORDER-001",
                                1,
                                0.79,
                                "2026-04-22T10:00:00",
                                "2026-04-24T10:00:00",
                                "2026-04-22T09:59:58",
                                "2026-04-22T10:00:00"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tsdbWrite.writeStatus").value("SUCCESS"));

        Assertions.assertEquals(
                1,
                tsMetricRepository.countByDeviceIdAndMetricCodeAndBatchNoAndSeqNo("DEV-001", "PRESSURE", "BATCH-B16-ORDER-001", 2L)
        );
        Assertions.assertEquals(
                1,
                tsMetricRepository.countByDeviceIdAndMetricCodeAndBatchNoAndSeqNo("DEV-001", "PRESSURE", "BATCH-B16-ORDER-001", 1L)
        );
    }

    @Test
    void missingMetadataOrFalseBackfillReturns4xx() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        Map<String, Object> missingOriginalSampleTime = backfillRequest(
                "BATCH-B16-INVALID-001",
                1,
                0.88,
                "2026-04-22T11:00:00",
                "2026-04-24T11:00:00",
                "2026-04-22T10:59:58",
                "2026-04-22T11:00:00"
        );
        missingOriginalSampleTime.remove("originalSampleTime");

        Map<String, Object> missingBatchNo = backfillRequest(
                "BATCH-B16-INVALID-002",
                1,
                0.88,
                "2026-04-22T11:00:00",
                "2026-04-24T11:00:00",
                "2026-04-22T10:59:58",
                "2026-04-22T11:00:00"
        );
        missingBatchNo.remove("batchNo");

        Map<String, Object> missingSeqNo = backfillRequest(
                "BATCH-B16-INVALID-003",
                1,
                0.88,
                "2026-04-22T11:00:00",
                "2026-04-24T11:00:00",
                "2026-04-22T10:59:58",
                "2026-04-22T11:00:00"
        );
        missingSeqNo.remove("seqNo");

        Map<String, Object> falseBackfill = backfillRequest(
                "BATCH-B16-INVALID-004",
                1,
                0.88,
                "2026-04-22T11:00:00",
                "2026-04-24T11:00:00",
                "2026-04-22T10:59:58",
                "2026-04-22T11:00:00"
        );
        falseBackfill.put("isBackfill", false);

        assertBackfillBadRequest(adminToken, missingOriginalSampleTime);
        assertBackfillBadRequest(adminToken, missingBatchNo);
        assertBackfillBadRequest(adminToken, missingSeqNo);
        assertBackfillBadRequest(adminToken, falseBackfill);
    }

    private void assertBackfillBadRequest(TokenPairResponse adminToken, Map<String, Object> request) throws Exception {
        mockMvc.perform(post("/api/ingest/backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BACKFILL_PAYLOAD_INVALID"));
    }

    private Map<String, Object> backfillRequest(
            String batchNo,
            long seqNo,
            double value,
            String eventTime,
            String recvTime,
            String deviceTime,
            String originalSampleTime
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("protocolType", "MQTT");
        request.put("sourceType", "EDGE_GATEWAY");
        request.put("sourceKey", "EDGE-HZ-GW-B16");
        request.put("traceId", "TRACE-" + batchNo + "-" + seqNo);
        request.put("batchNo", batchNo);
        request.put("seqNo", seqNo);
        request.put("originalSampleTime", originalSampleTime);
        request.put("isBackfill", true);
        request.put("metrics", List.of(Map.of(
                "deviceId", "DEV-001",
                "metricCode", "pressure",
                "value", value,
                "eventTime", eventTime,
                "recvTime", recvTime,
                "deviceTime", deviceTime
        )));
        return request;
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
