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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationScopeIntegrationTest {

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
    void snapshotShowsExpandedB07ScopeFields() throws Exception {
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/authz/users/U-B07-HZ-001/snapshot")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("U-B07-HZ-001"))
                .andExpect(jsonPath("$.data.dataScopeRule").value("REGION_ONLY"))
                .andExpect(jsonPath("$.data.authorizedRegions[0]").value("REGION-HZ"))
                .andExpect(jsonPath("$.data.dataViewConstraint").value("DETAIL"));
    }

    @Test
    void regionalDispatcherCanReadOwnRegionButNotOtherRegion() throws Exception {
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/object-dictionary")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.counts.segment").value(1))
                .andExpect(jsonPath("$.data.counts.node").value(2));

        mockMvc.perform(get("/api/object-chain/segment/SEG-001")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.segmentId").value("SEG-001"));

        mockMvc.perform(get("/api/object-chain/segment/SEG-002")
                        .header("Authorization", "Bearer " + regionalToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));
    }

    @Test
    void inspectorOwnScopeAndAuthzChecksAreEnforced() throws Exception {
        TokenPairResponse inspectorToken = localTokenService.issueForUser("U-INSPECT-001", "127.0.0.1", "JUnit");
        TokenPairResponse algorithmToken = localTokenService.issueForUser("U-ALGO-001", "127.0.0.1", "JUnit");
        TokenPairResponse leaderToken = localTokenService.issueForUser("U-LEADER-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/object-chain/node/NODE-001")
                        .header("Authorization", "Bearer " + inspectorToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodeId").value("NODE-001"));

        mockMvc.perform(get("/api/object-chain/node/NODE-003")
                        .header("Authorization", "Bearer " + inspectorToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));

        mockMvc.perform(post("/api/authz/check")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken())
                        .contentType("application/json")
                        .content("""
                                {"userId":"U-ALGO-001","entryPermission":"ENTRY:DIAG","menuPermission":"MENU:MODEL:READ","dataView":"MASKED_FEATURE","topic":"diag.model.inference"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allowed").value(true));

        mockMvc.perform(post("/api/authz/check")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken())
                        .contentType("application/json")
                        .content("""
                                {"userId":"U-ALGO-001","entryPermission":"ENTRY:DIAG","menuPermission":"MENU:MODEL:READ","dataView":"DETAIL","topic":"diag.model.inference"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_DENIED"));

        mockMvc.perform(post("/api/authz/check")
                        .header("Authorization", "Bearer " + leaderToken.accessToken())
                        .contentType("application/json")
                        .content("""
                                {"userId":"U-LEADER-001","entryPermission":"ENTRY:EMGC","menuPermission":"MENU:DASHBOARD:READ","dataView":"AGGREGATED","topic":"city.aggregate.overview"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allowed").value(true));

        mockMvc.perform(post("/api/authz/check")
                        .header("Authorization", "Bearer " + leaderToken.accessToken())
                        .contentType("application/json")
                        .content("""
                                {"userId":"U-LEADER-001","entryPermission":"ENTRY:EMGC","menuPermission":"MENU:ASSET:WRITE","dataView":"AGGREGATED","topic":"city.aggregate.overview"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MENU_PERMISSION_DENIED"));
    }

    @Test
    void topicCheckAndSubscribeRespectRegionalScope() throws Exception {
        TokenPairResponse regionalToken = localTokenService.issueForUser("U-B07-HZ-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/authz/topics/check")
                        .header("Authorization", "Bearer " + regionalToken.accessToken())
                        .contentType("application/json")
                        .content("""
                                {"userId":"U-B07-HZ-001","entryPermission":"ENTRY:EMGC","menuPermission":"MENU:WORKORDER:READ","regionId":"REGION-HZ","requestedTopics":["region.REGION-HZ.alerts.critical","region.REGION-BINJIANG.alerts.critical"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOPIC_SCOPE_DENIED"))
                .andExpect(jsonPath("$.data.allowedTopics[0]").value("region.REGION-HZ.alerts.critical"))
                .andExpect(jsonPath("$.data.deniedTopics[0]").value("region.REGION-BINJIANG.alerts.critical"));

        mockMvc.perform(post("/api/authz/topics/subscribe")
                        .header("Authorization", "Bearer " + regionalToken.accessToken())
                        .contentType("application/json")
                        .content("""
                                {"userId":"U-B07-HZ-001","entryPermission":"ENTRY:EMGC","menuPermission":"MENU:WORKORDER:READ","regionId":"REGION-HZ","requestedTopics":["region.REGION-HZ.alerts.critical"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allAllowed").value(true));

        mockMvc.perform(post("/api/authz/topics/subscribe")
                        .header("Authorization", "Bearer " + regionalToken.accessToken())
                        .contentType("application/json")
                        .content("""
                                {"userId":"U-B07-HZ-001","entryPermission":"ENTRY:EMGC","menuPermission":"MENU:WORKORDER:READ","regionId":"REGION-HZ","requestedTopics":["region.REGION-BINJIANG.alerts.critical"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUBSCRIPTION_NOT_ALLOWED"));
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
