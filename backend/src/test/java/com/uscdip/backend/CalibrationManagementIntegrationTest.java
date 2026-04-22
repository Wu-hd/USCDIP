package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.CalibrationProfileEntity;
import com.uscdip.backend.entity.DeviceEntity;
import com.uscdip.backend.entity.TsMetricEntity;
import com.uscdip.backend.repository.CalibrationProfileRepository;
import com.uscdip.backend.repository.DeviceRepository;
import com.uscdip.backend.repository.IncidentRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.repository.TsMetricRepository;
import com.uscdip.backend.repository.WorkOrderRepository;
import com.uscdip.backend.service.CalibrationManagementService;
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
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CalibrationManagementIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private TsMetricRepository tsMetricRepository;

    @Autowired
    private CalibrationProfileRepository calibrationProfileRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private ObjectScopeBindingRepository objectScopeBindingRepository;

    @Autowired
    private CalibrationManagementService calibrationManagementService;

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
    void createProfileActivatesNewVersionAndSupersedesPreviousActiveVersion() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String profileVersion = "CAL-JUNIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        mockMvc.perform(post("/api/calibration/devices/DEV-001/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("profileVersion", profileVersion),
                                Map.entry("metricCode", "PRESSURE"),
                                Map.entry("calibratedAt", "2026-04-23T09:00:00"),
                                Map.entry("effectiveFrom", "2026-04-23T09:00:00"),
                                Map.entry("effectiveUntil", "2026-10-31T23:59:59"),
                                Map.entry("operatorName", "赵工"),
                                Map.entry("referenceStandard", "STD-PRESSURE-JUNIT"),
                                Map.entry("correctionSlope", 1.02),
                                Map.entry("correctionOffset", -0.01),
                                Map.entry("driftThresholdAbs", 0.15),
                                Map.entry("driftThresholdPct", 8.0),
                                Map.entry("activate", true)
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profileVersion").value(profileVersion))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/calibration/devices/DEV-001/profiles")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].profileVersion", hasItems(profileVersion, "CAL-2026-02")))
                .andExpect(jsonPath("$.data[?(@.profileVersion=='CAL-2026-02')].status").value(hasItems("SUPERSEDED")));

        mockMvc.perform(get("/api/calibration/devices/DEV-001/profiles/active")
                        .param("metricCode", "PRESSURE")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(profileVersion));

        DeviceEntity device = deviceRepository.findById("DEV-001").orElseThrow();
        Assertions.assertEquals(LocalDateTime.parse("2026-10-31T23:59:59"), device.getCalibrationDueAt());
    }

    @Test
    void correctedPreviewDoesNotOverwriteRawMetricValue() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TsMetricEntity before = tsMetricRepository.findById("INGR-SEED-001").orElseThrow();

        mockMvc.perform(get("/api/calibration/metrics/INGR-SEED-001/corrected")
                        .param("profileVersion", "CAL-2026-02")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rawValue").value(0.86))
                .andExpect(jsonPath("$.data.profileVersion").value("CAL-2026-02"))
                .andExpect(jsonPath("$.data.correctedValue").value(0.883))
                .andExpect(jsonPath("$.data.calibrationExpired").value(false));

        TsMetricEntity after = tsMetricRepository.findById("INGR-SEED-001").orElseThrow();
        Assertions.assertEquals(before.getMetricValue(), after.getMetricValue());
    }

    @Test
    void confirmedDriftCreatesIncidentAndWorkOrder() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String profileVersion = "CAL-DRIFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        mockMvc.perform(post("/api/calibration/devices/DEV-002/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("profileVersion", profileVersion),
                                Map.entry("metricCode", "40001"),
                                Map.entry("calibratedAt", "2026-04-23T11:00:00"),
                                Map.entry("effectiveFrom", "2026-04-23T11:00:00"),
                                Map.entry("effectiveUntil", "2026-08-31T23:59:59"),
                                Map.entry("operatorName", "王工"),
                                Map.entry("referenceStandard", "STD-FLOW-JUNIT"),
                                Map.entry("correctionSlope", 1.0),
                                Map.entry("correctionOffset", 0.0),
                                Map.entry("driftThresholdAbs", 1.0),
                                Map.entry("driftThresholdPct", 3.0),
                                Map.entry("activate", true)
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/calibration/devices/DEV-002/drift-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "profileVersion", profileVersion,
                                "metricCode", "40001",
                                "observedValue", 50.0,
                                "referenceValue", 40.0,
                                "checkedAt", "2026-04-23T12:00:00",
                                "checkedBy", "zhangsan"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.driftStatus").value("CONFIRMED"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String incidentId = response.path("data").path("incidentId").asText();
        String workOrderId = response.path("data").path("workOrderId").asText();
        Assertions.assertTrue(incidentRepository.findById(incidentId).isPresent());
        Assertions.assertTrue(workOrderRepository.findById(workOrderId).isPresent());
        Assertions.assertTrue(objectScopeBindingRepository.findByObjectTypeAndObjectId("INCIDENT", incidentId).isPresent());
        Assertions.assertTrue(objectScopeBindingRepository.findByObjectTypeAndObjectId("WORK_ORDER", workOrderId).isPresent());
    }

    @Test
    void reminderScanCreatesWorkOrderOnceForSameProfile() {
        String profileId = "CALP-SCAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        calibrationProfileRepository.save(new CalibrationProfileEntity(
                profileId,
                "DEV-001",
                "CAL-SCAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "TEMPERATURE",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(2),
                "测试员",
                "STD-SCAN",
                new BigDecimal("1.000000"),
                new BigDecimal("0.000000"),
                new BigDecimal("0.100000"),
                new BigDecimal("5.000000"),
                "ACTIVE",
                LocalDateTime.now(),
                LocalDateTime.now()
        ));

        int first = calibrationManagementService.runDueReminderScan();
        int second = calibrationManagementService.runDueReminderScan();

        Assertions.assertEquals(1, first);
        Assertions.assertEquals(0, second);
    }

    @Test
    void calibrationQueriesRespectDataScope() throws Exception {
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/calibration/devices/DEV-003/profiles")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));

        mockMvc.perform(get("/api/calibration/metrics/INGR-SEED-004/corrected")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));
    }

    @Test
    void invalidDriftPayloadReturnsPlannedErrorCode() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/calibration/devices/DEV-001/drift-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "profileVersion", "CAL-2026-02",
                                "metricCode", "PRESSURE",
                                "observedValue", 1.10,
                                "referenceValue", 0.90,
                                "checkedBy", "zhangsan"
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CALIBRATION_DRIFT_EVALUATION_INVALID"))
                .andExpect(jsonPath("$.error.message", containsString("checkedAt")));
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
