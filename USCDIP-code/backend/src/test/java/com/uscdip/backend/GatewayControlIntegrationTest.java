package com.uscdip.backend;

import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.GatewayClientRuleEntity;
import com.uscdip.backend.entity.GatewayRiskAuditEntity;
import com.uscdip.backend.entity.GatewayRoutePolicyEntity;
import com.uscdip.backend.repository.GatewayClientRuleRepository;
import com.uscdip.backend.repository.GatewayRiskAuditRepository;
import com.uscdip.backend.repository.GatewayRoutePolicyRepository;
import com.uscdip.backend.service.GatewayRateLimitService;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "backend.oidc.enabled=true",
        "backend.oidc.issuer-uri=http://localhost:18081/realms/uscdip",
        "backend.oidc.access-token-secret=test-local-access-token-secret",
        "backend.gateway.body-size-limit-bytes=128",
        "backend.gateway.page-size-max=200",
        "backend.gateway.default-window-seconds=60",
        "backend.gateway.default-capacity=10"
})
@AutoConfigureMockMvc
class GatewayControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private GatewayRoutePolicyRepository gatewayRoutePolicyRepository;

    @Autowired
    private GatewayClientRuleRepository gatewayClientRuleRepository;

    @Autowired
    private GatewayRiskAuditRepository gatewayRiskAuditRepository;

    @Autowired
    private GatewayRateLimitService gatewayRateLimitService;

    @BeforeEach
    void setUp() {
        gatewayRiskAuditRepository.deleteAll();
        gatewayClientRuleRepository.deleteAll();
        gatewayRoutePolicyRepository.deleteAll();
        gatewayRateLimitService.clearAll();
    }

    @Test
    void publicSpecRouteRemainsAnonymous() throws Exception {
        savePolicy("GW-PUBLIC-MENU", "PUBLIC_MENU_BOUNDARIES", "/api/menu-boundaries", "GET",
                false, "LOW", "NONE", "IP", 100, 60, false);

        mockMvc.perform(get("/api/menu-boundaries")
                        .header("X-Trace-Id", "TRACE-GW-PUBLIC-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "TRACE-GW-PUBLIC-001"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void ipBlocklistRejectsRequestAndWritesAudit() throws Exception {
        savePolicy("GW-PUBLIC-MENU", "PUBLIC_MENU_BOUNDARIES", "/api/menu-boundaries", "GET",
                false, "LOW", "NONE", "IP", 100, 60, false);
        saveRule("GW-RULE-IP-BLOCK", "BLOCKLIST", "IP", "203.0.113.77", "Blocked integration-test IP");

        mockMvc.perform(get("/api/menu-boundaries")
                        .header("X-Forwarded-For", "203.0.113.77")
                        .header("X-Trace-Id", "TRACE-GW-BLOCK-IP-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GATEWAY_BLOCKED"));

        List<GatewayRiskAuditEntity> audits = gatewayRiskAuditRepository.findByTraceId("TRACE-GW-BLOCK-IP-001");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getDecision()).isEqualTo("DENY");
        assertThat(audits.get(0).getReasonCode()).isEqualTo("BLOCKLIST_MATCHED");
        assertThat(audits.get(0).getClientIp()).isEqualTo("203.0.113.77");
    }

    @Test
    void userBlocklistRejectsAuthenticatedRequestAndWritesAudit() throws Exception {
        savePolicy("GW-AUTH-ME", "AUTH_ME", "/api/auth/me", "GET",
                true, "MEDIUM", "NONE", "USER", 50, 60, false);
        saveRule("GW-RULE-USER-BLOCK", "BLOCKLIST", "USER", "U-B06-BLOCKED-001", "Blocked integration-test user");
        TokenPairResponse tokenPairResponse = localTokenService.issueForUser("U-B06-BLOCKED-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokenPairResponse.accessToken())
                        .header("X-Trace-Id", "TRACE-GW-BLOCK-USER-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GATEWAY_BLOCKED"));

        List<GatewayRiskAuditEntity> audits = gatewayRiskAuditRepository.findByTraceId("TRACE-GW-BLOCK-USER-001");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getDecision()).isEqualTo("DENY");
        assertThat(audits.get(0).getReasonCode()).isEqualTo("BLOCKLIST_MATCHED");
        assertThat(audits.get(0).getUserId()).isEqualTo("U-B06-BLOCKED-001");
    }

    @Test
    void refreshRejectsUnsupportedContentType() throws Exception {
        savePolicy("GW-AUTH-REFRESH", "AUTH_REFRESH", "/api/auth/refresh", "POST",
                false, "HIGH", "JSON_BODY", "USER_OR_IP", 10, 60, true);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("text/plain")
                        .content("not-json")
                        .header("X-Trace-Id", "TRACE-GW-CONTENT-001"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CONTENT_TYPE"));

        List<GatewayRiskAuditEntity> audits = gatewayRiskAuditRepository.findByTraceId("TRACE-GW-CONTENT-001");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getReasonCode()).isEqualTo("UNSUPPORTED_CONTENT_TYPE");
    }

    @Test
    void refreshRejectsOversizedBody() throws Exception {
        savePolicy("GW-AUTH-REFRESH", "AUTH_REFRESH", "/api/auth/refresh", "POST",
                false, "HIGH", "JSON_BODY", "USER_OR_IP", 10, 60, true);
        String oversizedPayload = """
                {"refreshToken":"%s"}
                """.formatted("x".repeat(180));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(oversizedPayload)
                        .header("X-Trace-Id", "TRACE-GW-BODY-001"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REQUEST_BODY_TOO_LARGE"));

        List<GatewayRiskAuditEntity> audits = gatewayRiskAuditRepository.findByTraceId("TRACE-GW-BODY-001");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getReasonCode()).isEqualTo("REQUEST_BODY_TOO_LARGE");
    }

    @Test
    void pageEndpointRejectsOversizedPageSize() throws Exception {
        savePolicy("GW-OBJECT-PAGE", "PUBLIC_OBJECT_DICTIONARY_PAGE", "/api/object-dictionary/page", "GET",
                false, "LOW", "PAGE_QUERY", "IP", 100, 60, false);

        mockMvc.perform(get("/api/object-dictionary/page")
                        .param("page", "1")
                        .param("pageSize", "10000")
                        .header("X-Trace-Id", "TRACE-GW-PAGE-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));

        List<GatewayRiskAuditEntity> audits = gatewayRiskAuditRepository.findByTraceId("TRACE-GW-PAGE-001");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getReasonCode()).isEqualTo("INVALID_PAGE_SIZE");
    }

    @Test
    void highRiskRefreshWritesAllowAuditAndEmergencyLoginIsRateLimited() throws Exception {
        savePolicy("GW-AUTH-REFRESH", "AUTH_REFRESH", "/api/auth/refresh", "POST",
                false, "HIGH", "JSON_BODY", "USER_OR_IP", 10, 60, true);
        savePolicy("GW-EMERGENCY-LOGIN", "EMERGENCY_LOGIN", "/api/auth/emergency/login", "POST",
                false, "HIGH", "JSON_BODY", "IP", 1, 3600, true);

        TokenPairResponse tokenPairResponse = localTokenService.issueForUser("U-DISPATCH-001", "127.0.0.1", "JUnit");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokenPairResponse.refreshToken()))
                        .header("X-Trace-Id", "TRACE-GW-ALLOW-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        List<GatewayRiskAuditEntity> allowAudits = gatewayRiskAuditRepository.findByTraceId("TRACE-GW-ALLOW-001");
        assertThat(allowAudits).hasSize(1);
        assertThat(allowAudits.get(0).getDecision()).isEqualTo("ALLOW");
        assertThat(allowAudits.get(0).getReasonCode()).isEqualTo("ALLOW");
        assertThat(allowAudits.get(0).getRouteCode()).isEqualTo("AUTH_REFRESH");

        mockMvc.perform(post("/api/auth/emergency/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"bg_active_hz","password":"wrong-password"}
                                """)
                        .header("X-Forwarded-For", "198.51.100.99")
                        .header("X-Trace-Id", "TRACE-GW-RATE-ALLOW-001"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/emergency/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"bg_active_hz","password":"wrong-password"}
                                """)
                        .header("X-Forwarded-For", "198.51.100.99")
                        .header("X-Trace-Id", "TRACE-GW-RATE-DENY-001"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));

        List<GatewayRiskAuditEntity> denyAudits = gatewayRiskAuditRepository.findByTraceId("TRACE-GW-RATE-DENY-001");
        assertThat(denyAudits).hasSize(1);
        assertThat(denyAudits.get(0).getDecision()).isEqualTo("DENY");
        assertThat(denyAudits.get(0).getReasonCode()).isEqualTo("RATE_LIMITED");
        assertThat(denyAudits.get(0).getRouteCode()).isEqualTo("EMERGENCY_LOGIN");
    }

    private void savePolicy(
            String policyId,
            String routeCode,
            String pathPattern,
            String httpMethod,
            boolean authRequired,
            String riskLevel,
            String validationProfile,
            String rateLimitScope,
            Integer rateLimitCapacity,
            Integer rateLimitWindowSeconds,
            boolean auditEnabled
    ) {
        LocalDateTime now = LocalDateTime.now();
        gatewayRoutePolicyRepository.save(new GatewayRoutePolicyEntity(
                policyId,
                routeCode,
                pathPattern,
                httpMethod,
                authRequired,
                riskLevel,
                validationProfile,
                rateLimitScope,
                rateLimitCapacity,
                rateLimitWindowSeconds,
                auditEnabled,
                true,
                now,
                now
        ));
    }

    private void saveRule(String ruleId, String ruleType, String subjectType, String subjectValue, String reason) {
        LocalDateTime now = LocalDateTime.now();
        gatewayClientRuleRepository.save(new GatewayClientRuleEntity(
                ruleId,
                ruleType,
                subjectType,
                subjectValue,
                reason,
                now.plusHours(1),
                true,
                now,
                now
        ));
    }
}
