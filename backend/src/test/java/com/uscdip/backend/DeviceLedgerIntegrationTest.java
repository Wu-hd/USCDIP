package com.uscdip.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.repository.DeviceHeartbeatRepository;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
class DeviceLedgerIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceHeartbeatRepository deviceHeartbeatRepository;

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
    void regionalDispatcherSeesOnlyAuthorizedDeviceLedgerRecords() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/device-ledger/devices")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[*].deviceId", hasItems("DEV-001", "DEV-002")))
                .andExpect(jsonPath("$.data.items[*].deviceId", not(hasItems("DEV-003"))));
    }

    @Test
    void registerSupportsCreateAndUpsert() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        String createPayload = objectMapper.writeValueAsString(Map.of(
                "deviceId", "DEV-004",
                "deviceName", "液位计-04",
                "facilityId", "FAC-002",
                "segmentId", "SEG-001",
                "nodeId", "NODE-002",
                "protocolType", "mqtt",
                "calibrationDueAt", LocalDateTime.now().plusDays(20)
        ));

        mockMvc.perform(post("/api/device-ledger/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload)
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deviceId").value("DEV-004"))
                .andExpect(jsonPath("$.data.onlineStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.data.calibrationExpired").value(false));

        mockMvc.perform(get("/api/device-ledger/devices/DEV-004")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionId").value("REGION-HZ"));

        String upsertPayload = objectMapper.writeValueAsString(Map.of(
                "deviceId", "DEV-004",
                "deviceName", "液位计-04-升级版",
                "facilityId", "FAC-002",
                "segmentId", "SEG-001",
                "nodeId", "NODE-002",
                "protocolType", "nb-iot",
                "calibrationDueAt", LocalDateTime.now().plusDays(30)
        ));

        mockMvc.perform(post("/api/device-ledger/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upsertPayload)
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceName").value("液位计-04-升级版"))
                .andExpect(jsonPath("$.data.protocolType").value("NB-IOT"));
    }

    @Test
    void heartbeatPersistsHistoryAndComputesStatuses() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        long before = deviceHeartbeatRepository.count();
        LocalDateTime now = LocalDateTime.now();

        mockMvc.perform(post("/api/device-ledger/devices/DEV-001/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "heartbeatTime", now.minusSeconds(5),
                                "recvTime", now.minusSeconds(3),
                                "bufferLevel", 20,
                                "abnormalFlags", List.of()
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineStatus").value("ONLINE"))
                .andExpect(jsonPath("$.data.onlineStatusReason").value("HEARTBEAT_OK"));

        mockMvc.perform(post("/api/device-ledger/devices/DEV-001/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "heartbeatTime", now.minusSeconds(4),
                                "recvTime", now.minusSeconds(2),
                                "bufferLevel", 85,
                                "abnormalFlags", List.of("sensor_fault")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineStatus").value("WARNING"))
                .andExpect(jsonPath("$.data.onlineStatusReason").value("BUFFER_LEVEL_HIGH_AND_ABNORMAL"));

        mockMvc.perform(post("/api/device-ledger/devices/DEV-001/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "heartbeatTime", now.minusSeconds(120),
                                "recvTime", now.minusSeconds(110),
                                "bufferLevel", 10,
                                "abnormalFlags", List.of()
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.data.onlineStatusReason").value("HEARTBEAT_TIMEOUT"));

        DeviceEntity device = deviceRepository.findById("DEV-001").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("OFFLINE", device.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(before + 3, deviceHeartbeatRepository.count());
    }

    @Test
    void invalidRequestsAndScopeDenialReturnExpectedErrors() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");
        LocalDateTime now = LocalDateTime.now();

        mockMvc.perform(post("/api/device-ledger/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceId", "DEV-005",
                                "deviceName", "非法设备",
                                "facilityId", "FAC-001",
                                "segmentId", "SEG-001",
                                "nodeId", "NODE-003",
                                "protocolType", "mqtt",
                                "calibrationDueAt", now.plusDays(5)
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DEVICE_RELATION_INVALID"));

        mockMvc.perform(post("/api/device-ledger/devices/DEV-001/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "heartbeatTime", now.minusSeconds(10),
                                "recvTime", now.minusSeconds(20),
                                "bufferLevel", 120,
                                "abnormalFlags", List.of("late")
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DEVICE_HEARTBEAT_INVALID"));

        mockMvc.perform(get("/api/device-ledger/devices/DEV-003")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));
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
