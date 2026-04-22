package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.IngestRecordEntity;
import com.uscdip.backend.repository.IngestBatchRepository;
import com.uscdip.backend.repository.IngestRecordRepository;
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

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UnifiedIngestIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private IngestBatchRepository ingestBatchRepository;

    @Autowired
    private IngestRecordRepository ingestRecordRepository;

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
    void ingestMetricsPersistsBatchAndSeparatesTimes() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        long batchCountBefore = ingestBatchRepository.count();

        MvcResult result = mockMvc.perform(post("/api/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "protocolType", "MQTT",
                                "sourceType", "EDGE_GATEWAY",
                                "sourceKey", "EDGE-HZ-GW-02",
                                "traceId", "TRACE-INGEST-JUNIT-001",
                                "isBackfill", false,
                                "metrics", List.of(
                                        Map.of(
                                                "deviceId", "DEV-001",
                                                "metricCode", "pressure",
                                                "value", 0.92,
                                                "eventTime", "2026-04-23T09:00:00",
                                                "recvTime", "2026-04-23T09:00:03",
                                                "deviceTime", "2026-04-23T08:59:58",
                                                "attributes", Map.of("topic", "region/hz/dev-001/pressure", "qos", 1)
                                        ),
                                        Map.of(
                                                "deviceId", "DEV-001",
                                                "metricCode", "temperature",
                                                "value", 19.6,
                                                "eventTime", "2026-04-23T09:00:10",
                                                "recvTime", "2026-04-23T09:00:11",
                                                "deviceTime", "2026-04-23T09:00:09",
                                                "attributes", Map.of("topic", "region/hz/dev-001/temp")
                                        )
                                )
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.protocolType").value("MQTT"))
                .andExpect(jsonPath("$.data.totalRecordCount").value(2))
                .andExpect(jsonPath("$.data.records[0].metricCode").value("PRESSURE"))
                .andExpect(jsonPath("$.data.records[0].eventTime").value("2026-04-23T09:00:00"))
                .andExpect(jsonPath("$.data.records[0].recvTime").value("2026-04-23T09:00:03"))
                .andExpect(jsonPath("$.data.records[0].deviceTime").value("2026-04-23T08:59:58"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String batchId = response.path("data").path("batchId").asText();
        List<IngestRecordEntity> records = ingestRecordRepository.findByBatchIdOrderByEventTimeAsc(batchId);

        Assertions.assertEquals(batchCountBefore + 1, ingestBatchRepository.count());
        Assertions.assertEquals(2, records.size());
        Assertions.assertEquals(LocalDateTime.parse("2026-04-23T09:00:00"), records.get(0).getEventTime());
        Assertions.assertEquals(LocalDateTime.parse("2026-04-23T09:00:03"), records.get(0).getRecvTime());
        Assertions.assertEquals(LocalDateTime.parse("2026-04-23T08:59:58"), records.get(0).getDeviceTime());
    }

    @Test
    void adaptEndpointUsesProtocolAdapterSkeleton() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/ingest/adapt/MODBUS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceType", "PLC_GATEWAY",
                                "sourceKey", "PLC-HZ-02",
                                "traceId", "TRACE-INGEST-JUNIT-ADAPT-001",
                                "isBackfill", false,
                                "payload", Map.of(
                                        "deviceId", "DEV-002",
                                        "registerAddress", "40001",
                                        "registerValue", 41.8,
                                        "sampledAt", "2026-04-23T10:00:00",
                                        "gatewayReceivedAt", "2026-04-23T10:00:02",
                                        "controllerTime", "2026-04-23T09:59:59",
                                        "slaveId", "8"
                                )
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.protocolType").value("MODBUS"))
                .andExpect(jsonPath("$.data.adaptedCount").value(1))
                .andExpect(jsonPath("$.data.adaptedMetrics[0].metricCode").value("40001"))
                .andExpect(jsonPath("$.data.adaptedMetrics[0].recvTime").value("2026-04-23T10:00:02"))
                .andExpect(jsonPath("$.data.batch.status").value("ADAPTED"));
    }

    @Test
    void ingestSeedDataRespectsDataScope() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/ingest/batches")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[*].batchId", hasItems("INGB-SEED-MQTT-001", "INGB-SEED-MODBUS-001")))
                .andExpect(jsonPath("$.data.items[*].batchId", not(hasItems("INGB-SEED-NBIOT-001"))));

        mockMvc.perform(get("/api/ingest/batches/INGB-SEED-NBIOT-001")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));

        mockMvc.perform(get("/api/ingest/batches/INGB-SEED-NBIOT-001")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protocolType").value("NB_IOT"))
                .andExpect(jsonPath("$.data.records[0].isBackfill").value(true))
                .andExpect(jsonPath("$.data.records[0].traceId").value("TRACE-INGEST-SEED-003"));
    }

    @Test
    void invalidPayloadAndMissingDeviceReturnPlannedErrors() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "protocolType", "OPCUA",
                                "sourceType", "EDGE_GATEWAY",
                                "sourceKey", "EDGE-HZ-GW-02",
                                "traceId", "TRACE-INGEST-JUNIT-002",
                                "isBackfill", false,
                                "metrics", List.of(
                                        Map.of(
                                                "deviceId", "DEV-001",
                                                "metricCode", "pressure",
                                                "value", 1,
                                                "eventTime", "2026-04-23T09:00:00",
                                                "recvTime", "2026-04-23T09:00:01",
                                                "deviceTime", "2026-04-23T09:00:00"
                                        )
                                )
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INGEST_PROTOCOL_UNSUPPORTED"));

        mockMvc.perform(post("/api/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "protocolType", "MQTT",
                                "sourceType", "EDGE_GATEWAY",
                                "sourceKey", "EDGE-HZ-GW-02",
                                "traceId", "TRACE-INGEST-JUNIT-003",
                                "isBackfill", false,
                                "metrics", List.of(
                                        Map.of(
                                                "deviceId", "DEV-404",
                                                "metricCode", "pressure",
                                                "value", 1,
                                                "eventTime", "2026-04-23T09:00:00",
                                                "recvTime", "2026-04-23T09:00:01",
                                                "deviceTime", "2026-04-23T09:00:00"
                                        )
                                )
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INGEST_DEVICE_NOT_FOUND"));

        mockMvc.perform(post("/api/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "protocolType":"MQTT",
                                  "sourceType":"EDGE_GATEWAY",
                                  "sourceKey":"EDGE-HZ-GW-02",
                                  "traceId":"TRACE-INGEST-JUNIT-004",
                                  "isBackfill":false,
                                  "metrics":[
                                    {
                                      "deviceId":"DEV-001",
                                      "metricCode":"pressure",
                                      "value":1,
                                      "eventTime":"not-a-time",
                                      "recvTime":"2026-04-23T09:00:01",
                                      "deviceTime":"2026-04-23T09:00:00"
                                    }
                                  ]
                                }
                                """)
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INGEST_PAYLOAD_INVALID"));

        mockMvc.perform(post("/api/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "protocolType":"MQTT",
                                  "sourceType":"EDGE_GATEWAY",
                                  "sourceKey":"EDGE-HZ-GW-02",
                                  "traceId":"TRACE-INGEST-JUNIT-005",
                                  "isBackfill":false,
                                  "metrics":[]
                                }
                                """)
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INGEST_PAYLOAD_INVALID"));
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
