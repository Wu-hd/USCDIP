package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.MasterDataAuditEntity;
import com.uscdip.backend.entity.ObjectVersionEntity;
import com.uscdip.backend.repository.MasterDataAuditRepository;
import com.uscdip.backend.repository.ObjectVersionRepository;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MasterDataChangeIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectVersionRepository objectVersionRepository;

    @Autowired
    private MasterDataAuditRepository masterDataAuditRepository;

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
    void pendingSegmentChangeCreatesQueuedFollowupRequest() throws Exception {
        String adminAccessToken = issueAccessToken("U-ADMIN-001");

        mockMvc.perform(post("/api/master/changes")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "objectType":"SEGMENT",
                                  "objectId":"SEG-001",
                                  "baseVersionNo":1,
                                  "reason":"queue-after-existing-pending",
                                  "payload":{
                                    "segmentName":"北区主干一段-排队审批"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestStatus").value("QUEUED"))
                .andExpect(jsonPath("$.data.objectType").value("SEGMENT"))
                .andExpect(jsonPath("$.data.objectId").value("SEG-001"));
    }

    @Test
    void staleBaseVersionIsRejectedWithConflict() throws Exception {
        String adminAccessToken = issueAccessToken("U-ADMIN-001");

        mockMvc.perform(post("/api/master/changes")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "objectType":"DEVICE",
                                  "objectId":"DEV-002",
                                  "baseVersionNo":0,
                                  "reason":"stale-version",
                                  "payload":{
                                    "deviceName":"流量计-02-旧版本提交"
                                  }
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MASTER_DATA_VERSION_CONFLICT"));
    }

    @Test
    void nonAdminCannotApproveChangeRequest() throws Exception {
        String adminAccessToken = issueAccessToken("U-ADMIN-001");
        String regionalAccessToken = issueAccessToken("U-B07-HZ-001");
        String requestId = submitChange(adminAccessToken, """
                {
                  "objectType":"DEVICE",
                  "objectId":"DEV-002",
                  "baseVersionNo":1,
                  "reason":"await-admin-approval",
                  "payload":{
                    "deviceName":"流量计-02-待审批"
                  }
                }
                """);

        mockMvc.perform(post("/api/master/changes/{requestId}/approve", requestId)
                        .header("Authorization", "Bearer " + regionalAccessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason":"should-not-pass"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void approveChangeUpdatesMasterDataAndWritesVersionAudit() throws Exception {
        String adminAccessToken = issueAccessToken("U-ADMIN-001");
        String requestId = submitChange(adminAccessToken, """
                {
                  "objectType":"DEVICE",
                  "objectId":"DEV-002",
                  "baseVersionNo":1,
                  "reason":"upgrade-device-metadata",
                  "payload":{
                    "deviceName":"流量计-02-升级版",
                    "protocolType":"NB-IOT"
                  }
                }
                """);

        mockMvc.perform(post("/api/master/changes/{requestId}/approve", requestId)
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason":"approved-for-b09"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.requestStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.effectiveVersionNo").value(2));

        mockMvc.perform(get("/api/master/devices/DEV-002")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.objectName").value("流量计-02-升级版"))
                .andExpect(jsonPath("$.data.attributes.versionNo").value(2))
                .andExpect(jsonPath("$.data.attributes.protocolType").value("NB-IOT"));

        assertThat(objectVersionRepository.findAll().stream()
                .filter(version -> "DEVICE".equalsIgnoreCase(version.getObjectType()))
                .filter(version -> "DEV-002".equalsIgnoreCase(version.getObjectId()))
                .map(ObjectVersionEntity::getVersionNo))
                .contains(2L);

        assertThat(masterDataAuditRepository.findAll().stream()
                .filter(audit -> "DEVICE".equalsIgnoreCase(audit.getObjectType()))
                .filter(audit -> "DEV-002".equalsIgnoreCase(audit.getObjectId()))
                .filter(audit -> "APPROVE".equalsIgnoreCase(audit.getAction()))
                .map(MasterDataAuditEntity::getOutcome))
                .contains("SUCCESS");
    }

    @Test
    void topologyChangeApprovalRefreshesObjectChainRelations() throws Exception {
        String adminAccessToken = issueAccessToken("U-ADMIN-001");
        String requestId = submitChange(adminAccessToken, """
                {
                  "objectType":"FACILITY",
                  "objectId":"FAC-002",
                  "baseVersionNo":1,
                  "reason":"move-facility-topology",
                  "payload":{
                    "segmentId":"SEG-002",
                    "nodeId":"NODE-003"
                  }
                }
                """);

        mockMvc.perform(post("/api/master/changes/{requestId}/approve", requestId)
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason":"topology-approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.effectiveVersionNo").value(2));

        mockMvc.perform(get("/api/object-chain/segment/SEG-002")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.facilities[*].facilityId", hasItems("FAC-002", "FAC-003")))
                .andExpect(jsonPath("$.data.devices[*].deviceId", hasItems("DEV-002", "DEV-003")));

        mockMvc.perform(get("/api/master/devices/DEV-002")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.relatedObjectIds.segmentIds[0]").value("SEG-002"))
                .andExpect(jsonPath("$.data.relatedObjectIds.nodeIds[0]").value("NODE-003"))
                .andExpect(jsonPath("$.data.attributes.versionNo").value(2));
    }

    @Test
    void approvedStationChangeUpdatesInfoLayoutAndGisPosition() throws Exception {
        String adminAccessToken = issueAccessToken("U-ADMIN-001");
        String requestId = submitChange(adminAccessToken, """
                {
                  "objectType":"STATION",
                  "objectId":"ST-HUT-ZZ-001",
                  "baseVersionNo":0,
                  "reason":"connect-station-layout",
                  "payload":{
                    "stationInfo":{"operator":"HUT"},
                    "pipelineLayout":{
                      "type":"FeatureCollection",
                      "features":[{
                        "type":"Feature",
                        "properties":{"name":"测试管线"},
                        "geometry":{"type":"LineString","coordinates":[[113.107,27.818],[113.108,27.819]]}
                      }]
                    }
                  }
                }
                """);

        mockMvc.perform(post("/api/master/changes/{requestId}/approve", requestId)
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.effectiveVersionNo").value(1));

        mockMvc.perform(get("/api/master/stations/ST-HUT-ZZ-001/pipeline-layout")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.features.length()").value(1))
                .andExpect(jsonPath("$.data.layout.features[0].geometry.type").value("LineString"));
    }

    @Test
    void stationChangeRejectsUnsupportedGeoJsonGeometry() throws Exception {
        String adminAccessToken = issueAccessToken("U-ADMIN-001");

        mockMvc.perform(post("/api/master/changes")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "objectType":"STATION",
                                  "objectId":"ST-HUT-ZZ-001",
                                  "baseVersionNo":0,
                                  "reason":"invalid-layout",
                                  "payload":{
                                    "pipelineLayout":{
                                      "type":"FeatureCollection",
                                      "features":[{
                                        "type":"Feature",
                                        "properties":{},
                                        "geometry":{"type":"Polygon","coordinates":[]}
                                      }]
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    private String issueAccessToken(String userId) {
        TokenPairResponse tokenPairResponse = localTokenService.issueForUser(userId, "127.0.0.1", "JUnit");
        return tokenPairResponse.accessToken();
    }

    private String submitChange(String accessToken, String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/master/changes")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String requestId = jsonNode.path("data").path("requestId").asText();
        assertThat(requestId).isNotBlank();
        return requestId;
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
