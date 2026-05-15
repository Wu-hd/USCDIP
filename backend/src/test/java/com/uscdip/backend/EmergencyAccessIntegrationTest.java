package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.entity.EmergencyAccountEntity;
import com.uscdip.backend.model.AuthMode;
import com.uscdip.backend.repository.EmergencyAccountRepository;
import com.uscdip.backend.service.LocalAccessTokenService;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EmergencyAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private LocalAccessTokenService localAccessTokenService;

    @Autowired
    private EmergencyAccountRepository emergencyAccountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("backend.oidc.enabled", () -> "false");
        registry.add("backend.oidc.access-token-secret", () -> "test-local-access-token-secret");
    }

    @Test
    void emergencyAccountCanBeActivatedAndUsedWithoutOidc() throws Exception {
        String adminAccessToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit").accessToken();
        String password = "P@" + UUID.randomUUID();
        String expiresAt = LocalDateTime.now().plusHours(2).withNano(0).toString();

        mockMvc.perform(post("/api/auth/emergency/accounts/EA-B05-INACTIVE-001/activate")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("""
                                {"password":"%s","expiresAt":"%s","reason":"integration drill"}
                                """.formatted(password, expiresAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value("EA-B05-INACTIVE-001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/emergency/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"bg_inactive_hz","password":"%s"}
                                """.formatted(password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.at("/data/accessToken").asText();
        String refreshToken = loginJson.at("/data/refreshToken").asText();
        String refreshedAt = loginJson.at("/data/refreshTokenExpiresAt").asText();

        LocalAccessTokenService.AccessTokenPrincipal principal = localAccessTokenService.verify(accessToken);
        Duration accessLifetime = Duration.between(principal.issuedAt(), principal.expiresAt());

        org.junit.jupiter.api.Assertions.assertEquals(AuthMode.BREAK_GLASS, principal.authMode());
        org.junit.jupiter.api.Assertions.assertEquals("EA-B05-INACTIVE-001", principal.emergencyAccountId());
        org.junit.jupiter.api.Assertions.assertEquals(600L, accessLifetime.getSeconds());
        org.junit.jupiter.api.Assertions.assertTrue(refreshedAt.contains("T"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authMode").value("BREAK_GLASS"))
                .andExpect(jsonPath("$.data.snapshot.userId").value("U-B05-COMMAND-001"))
                .andExpect(jsonPath("$.data.snapshot.roleCodes", hasItem("BREAK_GLASS_COMMAND")))
                .andExpect(jsonPath("$.data.snapshot.permissionCodes", hasItem("ENTRY:EMGC")))
                .andExpect(jsonPath("$.data.snapshot.permissionCodes", not(hasItem("ENTRY:MGMT"))))
                .andExpect(jsonPath("$.data.snapshot.permissionCodes", not(hasItem("MENU:MODEL:WRITE"))));

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String rotatedRefreshToken = refreshJson.at("/data/refreshToken").asText();

        mockMvc.perform(get("/api/auth/emergency/audit")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .param("username", "bg_inactive_hz")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.records[*].eventType", everyItem(org.hamcrest.Matchers.startsWith("BREAK_GLASS_"))));

        mockMvc.perform(post("/api/auth/emergency/accounts/EA-B05-INACTIVE-001/revoke")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("""
                                {"reason":"integration completed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(rotatedRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOKEN_REFRESH_REVOKED"));
    }

    @Test
    void expiredEmergencyAccountIsRejected() throws Exception {
        String adminAccessToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit").accessToken();
        String password = "P@" + UUID.randomUUID();
        String expiresAt = LocalDateTime.now().plusMinutes(30).withNano(0).toString();

        mockMvc.perform(post("/api/auth/emergency/accounts/EA-B05-INACTIVE-001/activate")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType("application/json")
                        .content("""
                                {"password":"%s","expiresAt":"%s","reason":"expiry test"}
                                """.formatted(password, expiresAt)))
                .andExpect(status().isOk());

        EmergencyAccountEntity account = emergencyAccountRepository.findById("EA-B05-INACTIVE-001").orElseThrow();
        account.setStatus("ACTIVE");
        account.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        account.setUpdatedAt(LocalDateTime.now());
        emergencyAccountRepository.save(account);

        mockMvc.perform(post("/api/auth/emergency/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"bg_inactive_hz","password":"%s"}
                                """.formatted(password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMERGENCY_ACCOUNT_NOT_AVAILABLE"));
    }
}
