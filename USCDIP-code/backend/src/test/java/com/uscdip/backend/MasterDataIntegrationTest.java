package com.uscdip.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataIntegrationTest {

    private static final HttpServer ISSUER_SERVER = startIssuerServer();
    private static final String ISSUER_URI = "http://127.0.0.1:" + ISSUER_SERVER.getAddress().getPort() + "/realms/uscdip";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

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
    void regionalDispatcherSeesOnlyAuthorizedMasterData() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/master/nodes")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].objectType").value("NODE"))
                .andExpect(jsonPath("$.data.items[0].objectId").value("NODE-001"))
                .andExpect(jsonPath("$.data.items[0].regionId").value("REGION-HZ"))
                .andExpect(jsonPath("$.data.items[0].relatedObjectIds.segmentIds[*]", hasItems("SEG-001")))
                .andExpect(jsonPath("$.data.items[0].relatedObjectIds.deviceIds[*]", hasItems("DEV-001")));
    }

    @Test
    void masterDetailEndpointsRespectDataScope() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/master/devices/DEV-001")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.objectType").value("DEVICE"))
                .andExpect(jsonPath("$.data.objectId").value("DEV-001"))
                .andExpect(jsonPath("$.data.relatedObjectIds.facilityIds[0]").value("FAC-001"))
                .andExpect(jsonPath("$.data.relatedObjectIds.segmentIds[0]").value("SEG-001"))
                .andExpect(jsonPath("$.data.relatedObjectIds.nodeIds[0]").value("NODE-001"));

        mockMvc.perform(get("/api/master/devices/DEV-003")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));
    }

    @Test
    void objectChainQueriesUseObjectRelationTopology() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/object-chain/segment/SEG-001")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.segmentId").value("SEG-001"))
                .andExpect(jsonPath("$.data.nodes[*].nodeId", hasItems("NODE-001", "NODE-002")))
                .andExpect(jsonPath("$.data.facilities[*].facilityId", hasItems("FAC-001", "FAC-002")))
                .andExpect(jsonPath("$.data.devices[*].deviceId", hasItems("DEV-001", "DEV-002")));
    }

    @Test
    void adminCanReadHunanStationAndEmptyPipelineLayout() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/master/stations")
                        .param("provinceAdcode", "430000")
                        .param("cityAdcode", "430200")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].objectType").value("STATION"))
                .andExpect(jsonPath("$.data.items[0].objectId").value("ST-HUT-ZZ-001"))
                .andExpect(jsonPath("$.data.items[0].objectName").value("湖南工业大学管网监测点"))
                .andExpect(jsonPath("$.data.items[0].attributes.longitude").value(113.107385))
                .andExpect(jsonPath("$.data.items[0].attributes.latitude").value(27.818289));

        mockMvc.perform(get("/api/master/stations/ST-HUT-ZZ-001/pipeline-layout")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layoutSrid").value("EPSG:4490"))
                .andExpect(jsonPath("$.data.layout.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.data.layout.features").isEmpty());
    }

    @Test
    void unrelatedRegionalUserCannotReadHunanStation() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/master/stations/ST-HUT-ZZ-001")
                        .header("Authorization", "Bearer " + token.accessToken()))
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
