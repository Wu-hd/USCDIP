package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("backend.oidc.enabled", () -> "false");
        registry.add("backend.oidc.access-token-secret", () -> "test-local-access-token-secret");
    }

    @Test
    void platformAdminCanListOptionsAndRegionalUserIsDenied() throws Exception {
        String adminToken = tokenFor("U-ADMIN-001");
        String regionalToken = tokenFor("U-DISPATCH-001");

        mockMvc.perform(get("/api/auth/admin/accounts/options")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roles[*].roleCode", hasItem("PLATFORM_ADMIN")))
                .andExpect(jsonPath("$.data.scopeTypes", hasItem("REGION")));

        mockMvc.perform(get("/api/auth/admin/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "admin")
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].username").value("admin"))
                .andExpect(jsonPath("$.data.items[0].roleCodes", hasItem("PLATFORM_ADMIN")));

        mockMvc.perform(get("/api/auth/admin/accounts")
                        .header("Authorization", "Bearer " + regionalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void accountLifecycleCreatesLoginRevokesSessionsAndResetsPassword() throws Exception {
        String adminToken = tokenFor("U-ADMIN-001");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "operator_" + suffix;
        String initialPassword = "Start1234!";
        String nextPassword = "Next5678!Pass";

        MvcResult createResult = mockMvc.perform(post("/api/auth/admin/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"%s",
                                  "displayName":"集成测试操作员",
                                  "primaryRegionId":"REGION-HZ",
                                  "password":"%s",
                                  "roleCodes":["REGIONAL_DISPATCHER"],
                                  "dataScopes":{"REGION":["REGION-HZ"]}
                                }
                                """.formatted(username, initialPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.localLoginEnabled").value(true))
                .andReturn();
        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String userId = createJson.at("/data/userId").asText();

        MvcResult loginResult = login(username, initialPassword)
                .andExpect(status().isOk())
                .andReturn();
        String userAccessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        mockMvc.perform(put("/api/auth/admin/accounts/{userId}", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "displayName":"停用操作员",
                                  "primaryRegionId":"REGION-HZ",
                                  "status":"DISABLED",
                                  "roleCodes":["REGIONAL_DISPATCHER"],
                                  "dataScopes":{"REGION":["REGION-HZ"]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.localLoginEnabled").value(false));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + userAccessToken))
                .andExpect(status().isUnauthorized());
        login(username, initialPassword).andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/auth/admin/accounts/{userId}", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "displayName":"重新启用操作员",
                                  "primaryRegionId":"REGION-HZ",
                                  "status":"ACTIVE",
                                  "roleCodes":["REGIONAL_DISPATCHER"],
                                  "dataScopes":{"REGION":["REGION-HZ"],"ASSIGNEE":["%s"]}
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/auth/admin/accounts/{userId}/password", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"password\":\"%s\"}".formatted(nextPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.localLoginEnabled").value(true));

        login(username, initialPassword).andExpect(status().isUnauthorized());
        login(username, nextPassword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userSnapshot.roleCodes", hasItem("REGIONAL_DISPATCHER")));
    }

    @Test
    void duplicateWeakPasswordAndSelfDisableAreRejected() throws Exception {
        String adminToken = tokenFor("U-ADMIN-001");

        mockMvc.perform(post("/api/auth/admin/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"admin",
                                  "displayName":"重复管理员",
                                  "primaryRegionId":"GLOBAL",
                                  "password":"Start1234!",
                                  "roleCodes":["PLATFORM_ADMIN"],
                                  "dataScopes":{}
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_CONFLICT"));

        mockMvc.perform(post("/api/auth/admin/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"weak_password_user",
                                  "displayName":"弱密码用户",
                                  "primaryRegionId":"REGION-HZ",
                                  "password":"123456",
                                  "roleCodes":["REGIONAL_DISPATCHER"],
                                  "dataScopes":{}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));

        mockMvc.perform(put("/api/auth/admin/accounts/U-ADMIN-001")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "displayName":"平台管理员",
                                  "primaryRegionId":"GLOBAL",
                                  "status":"DISABLED",
                                  "roleCodes":["PLATFORM_ADMIN"],
                                  "dataScopes":{}
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INVALID_OPERATION"));
    }

    private String tokenFor(String userId) {
        return localTokenService.issueForUser(userId, "127.0.0.1", "JUnit").accessToken();
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/emergency/login")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }
}
