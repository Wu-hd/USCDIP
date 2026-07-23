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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GisSpatialQueryIntegrationTest {

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
    void bboxQueryReturnsOnlyAuthorizedRegionalObjects() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/gis/objects/bbox")
                        .param("minX", "120.1500")
                        .param("minY", "30.2700")
                        .param("maxX", "120.1800")
                        .param("maxY", "30.3000")
                        .param("authoritySrid", "EPSG:4490")
                        .param("displaySrid", "EPSG:3857")
                        .param("objectType", "NODE")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[*].objectId", hasItems("NODE-001", "NODE-002")))
                .andExpect(jsonPath("$.data.items[0].displaySrid").value("EPSG:3857"))
                .andExpect(jsonPath("$.data.items[0].geometry2d", startsWith("POINT")));
    }

    @Test
    void objectPickReturnsNearestNodeWithRelatedIds() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/gis/objects/pick")
                        .header("Authorization", "Bearer " + token.accessToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "x":120.1533,
                                  "y":30.2741,
                                  "authoritySrid":"EPSG:4490",
                                  "displaySrid":"EPSG:3857",
                                  "objectTypes":["NODE"],
                                  "toleranceMeters":50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.object.objectType").value("NODE"))
                .andExpect(jsonPath("$.data.object.objectId").value("NODE-001"))
                .andExpect(jsonPath("$.data.object.relatedObjectIds.segmentIds[*]", hasItems("SEG-001")))
                .andExpect(jsonPath("$.data.distanceMeters").value(0.00));
    }

    @Test
    void gisObjectDetailIncludesGeometryAndObjectChainKeys() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/gis/objects/SEGMENT/SEG-001")
                        .param("displaySrid", "EPSG:3857")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.objectType").value("SEGMENT"))
                .andExpect(jsonPath("$.data.objectId").value("SEG-001"))
                .andExpect(jsonPath("$.data.geometry2d", startsWith("LINESTRING")))
                .andExpect(jsonPath("$.data.relatedObjectIds.nodeIds[*]", hasItems("NODE-001", "NODE-002")))
                .andExpect(jsonPath("$.data.relatedObjectIds.facilityIds[*]", hasItems("FAC-001", "FAC-002")));
    }

    @Test
    void coordinateServiceConvertsLineStringGeometry() throws Exception {
        mockMvc.perform(post("/api/gis/convert")
                        .contentType("application/json")
                        .content("""
                                {
                                  "authoritySrid":"EPSG:4490",
                                  "displaySrid":"EPSG:3857",
                                  "geometry2d":"LINESTRING(120.1533 30.2741,120.1634 30.2842)"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceSrid").value("EPSG:4490"))
                .andExpect(jsonPath("$.data.targetSrid").value("EPSG:3857"))
                .andExpect(jsonPath("$.data.convertedGeometry", startsWith("LINESTRING")))
                .andExpect(jsonPath("$.data.conversionMode").value("service"));
    }

    @Test
    void stationParticipatesInGisDetailBboxAndPick() throws Exception {
        TokenPairResponse token = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/gis/objects/STATION/ST-HUT-ZZ-001")
                        .param("displaySrid", "EPSG:4490")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectType").value("STATION"))
                .andExpect(jsonPath("$.data.objectName").value("湖南工业大学管网监测点"))
                .andExpect(jsonPath("$.data.geometry2d", startsWith("POINT")));

        mockMvc.perform(get("/api/gis/objects/bbox")
                        .param("minX", "113.10")
                        .param("minY", "27.81")
                        .param("maxX", "113.12")
                        .param("maxY", "27.83")
                        .param("authoritySrid", "EPSG:4490")
                        .param("displaySrid", "EPSG:4490")
                        .param("objectType", "STATION")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].objectId").value("ST-HUT-ZZ-001"));

        mockMvc.perform(post("/api/gis/objects/pick")
                        .header("Authorization", "Bearer " + token.accessToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "x":113.107385,
                                  "y":27.818289,
                                  "authoritySrid":"EPSG:4490",
                                  "displaySrid":"EPSG:4490",
                                  "objectTypes":["STATION"],
                                  "toleranceMeters":50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.object.objectId").value("ST-HUT-ZZ-001"));
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
