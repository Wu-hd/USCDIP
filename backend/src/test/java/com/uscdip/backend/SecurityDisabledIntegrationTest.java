package com.uscdip.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "backend.oidc.enabled=false")
@AutoConfigureMockMvc
class SecurityDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicSpecEndpointsRemainAnonymous() throws Exception {
        mockMvc.perform(get("/api/menu-boundaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/authz/matrix-spec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/authz/matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void protectedBusinessQueriesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/object-dictionary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/object-chain/segment/SEG-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/master/nodes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/master/changes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/gis/objects/bbox")
                        .param("minX", "120.10")
                        .param("minY", "30.20")
                        .param("maxX", "120.20")
                        .param("maxY", "30.30"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/master/changes")
                        .contentType("application/json")
                        .content("""
                                {
                                  "objectType":"DEVICE",
                                  "objectId":"DEV-001",
                                  "baseVersionNo":1,
                                  "reason":"security-disabled-test",
                                  "payload":{"deviceName":"ignored"}
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/gis/objects/pick")
                        .contentType("application/json")
                        .content("""
                                {
                                  "x":120.1533,
                                  "y":30.2741,
                                  "authoritySrid":"EPSG:4490",
                                  "displaySrid":"EPSG:3857",
                                  "objectTypes":["NODE"]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void oidcLoginEndpointsExposeDisabledState() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.registrationId").value("keycloak"))
                .andExpect(jsonPath("$.data.authorizationUrl").value(""));

        mockMvc.perform(get("/api/auth/login-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.authorizationUrl").value(""));
    }

    @Test
    void meAndLogoutReturnOidcDisabledWhenFeatureIsOff() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OIDC_DISABLED"));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OIDC_DISABLED"));
    }
}
