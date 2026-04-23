package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.ModelInvocationAuditEntity;
import com.uscdip.backend.entity.ModelResultEntity;
import com.uscdip.backend.entity.ObjectScopeBindingEntity;
import com.uscdip.backend.repository.ModelInvocationAuditRepository;
import com.uscdip.backend.repository.ModelResultRepository;
import com.uscdip.backend.repository.ObjectScopeBindingRepository;
import com.uscdip.backend.service.LocalTokenService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "backend.outbox.relay-fixed-delay-ms=3600000",
        "backend.notification.retry-fixed-delay-ms=3600000",
        "backend.websocket.bridge-fixed-delay-ms=3600000",
        "backend.websocket.idle-scan-fixed-delay-ms=3600000"
})
@AutoConfigureMockMvc
class ModelGatewayServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private ModelResultRepository modelResultRepository;

    @Autowired
    private ModelInvocationAuditRepository modelInvocationAuditRepository;

    @Autowired
    private ObjectScopeBindingRepository objectScopeBindingRepository;

    @Test
    void registerCreateGrayActivateAndRollbackModelVersion() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String modelCode = "B25_LIFECYCLE_MODEL";

        registerModel(adminToken, modelCode, 700)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.modelCode").value(modelCode));

        createVersion(adminToken, modelCode, "V1", 700)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/models/{modelCode}/versions/{versionNo}/activate", modelCode, "V1")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        createVersion(adminToken, modelCode, "V2", 600)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/models/{modelCode}/versions/{versionNo}/gray", modelCode, "V2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("grayPercent", 100)))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GRAY"))
                .andExpect(jsonPath("$.data.grayPercent").value(100));

        mockMvc.perform(post("/api/models/{modelCode}/infer", modelCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "MREQ-B25-GRAY-100",
                                "segmentId", "SEG-001",
                                "features", Map.of("pressureDrop", 0.44)
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultSource").value("MODEL"))
                .andExpect(jsonPath("$.data.versionNo").value("V2"));

        mockMvc.perform(post("/api/models/{modelCode}/rollback", modelCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetVersionNo", "V1", "reason", "rollback after gray test")))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value("V1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void inferenceSuccessWritesModelResultAndMaskedObjectScope() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String modelCode = "B25_SUCCESS_MODEL";
        registerModel(adminToken, modelCode, 1000).andExpect(status().isCreated());
        createVersion(adminToken, modelCode, "V1", 1000).andExpect(status().isCreated());
        mockMvc.perform(post("/api/models/{modelCode}/versions/{versionNo}/activate", modelCode, "V1")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/models/{modelCode}/infer", modelCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "MREQ-B25-SUCCESS",
                                "segmentId", "SEG-001",
                                "features", Map.of("pressureDrop", 0.61, "flowRate", 12.4)
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultSource").value("MODEL"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn();

        String modelResultId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("modelResultId").asText();
        ModelResultEntity modelResult = modelResultRepository.findById(modelResultId).orElseThrow();
        Assertions.assertEquals(modelCode, modelResult.getModelCode());
        Assertions.assertEquals("VALID", modelResult.getStatus());
        Assertions.assertEquals("SEG-001", modelResult.getSegmentId());

        ObjectScopeBindingEntity binding = objectScopeBindingRepository.findByObjectTypeAndObjectId("MODEL_RESULT", modelResultId).orElseThrow();
        Assertions.assertEquals("REGION-HZ", binding.getRegionId());
        Assertions.assertEquals("MASKED", binding.getScopeLevel());
    }

    @Test
    void timeoutAndNoActiveVersionFallBackToRuleChainWithAudit() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        MvcResult timeoutResult = mockMvc.perform(post("/api/models/CALIBRATION_DRIFT_GUARD/infer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "MREQ-B25-TIMEOUT-TEST",
                                "segmentId", "SEG-001",
                                "simulateLatencyMs", 900,
                                "features", Map.of("driftPct", 28.5)
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultSource").value("RULE_FALLBACK"))
                .andExpect(jsonPath("$.data.failureReason").value("MODEL_TIMEOUT"))
                .andReturn();

        String fallbackResultId = objectMapper.readTree(timeoutResult.getResponse().getContentAsString()).path("data").path("modelResultId").asText();
        Assertions.assertEquals("RULE_FALLBACK", modelResultRepository.findById(fallbackResultId).orElseThrow().getStatus());
        Assertions.assertTrue(modelInvocationAuditRepository.findByModelCodeOrderByCreatedAtDesc("CALIBRATION_DRIFT_GUARD").stream()
                .anyMatch(audit -> "MREQ-B25-TIMEOUT-TEST".equals(audit.getRequestId())
                        && "RULE_FALLBACK".equals(audit.getResultSource())
                        && "MODEL_TIMEOUT".equals(audit.getFailureReason())));

        String noActiveModel = "B25_NO_ACTIVE_MODEL";
        registerModel(adminToken, noActiveModel, 800).andExpect(status().isCreated());
        createVersion(adminToken, noActiveModel, "V1", 800).andExpect(status().isCreated());

        mockMvc.perform(post("/api/models/{modelCode}/infer", noActiveModel)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "MREQ-B25-NO-ACTIVE",
                                "nodeId", "NODE-001",
                                "features", Map.of("score", 0.35)
                        )))
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultSource").value("RULE_FALLBACK"))
                .andExpect(jsonPath("$.data.failureReason").value("NO_ACTIVE_VERSION"));
    }

    @Test
    void modelReadAndWritePermissionsAreSeparated() throws Exception {
        TokenPairResponse algorithmToken = localTokenService.issueForUser("U-ALGO-001", "127.0.0.1", "JUnit");

        MvcResult listResult = mockMvc.perform(get("/api/models")
                        .param("page", "1")
                        .param("pageSize", "50")
                        .header("Authorization", "Bearer " + algorithmToken.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data").path("items");
        Assertions.assertTrue(containsModel(items, "LEAK_DETECTOR"));

        mockMvc.perform(post("/api/models/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "modelCode", "B25_FORBIDDEN_MODEL",
                                "modelName", "forbidden model"
                        )))
                        .header("Authorization", "Bearer " + algorithmToken.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MENU_PERMISSION_DENIED"));
    }

    private org.springframework.test.web.servlet.ResultActions registerModel(TokenPairResponse token, String modelCode, int timeoutMs) throws Exception {
        return mockMvc.perform(post("/api/models/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "modelCode", modelCode,
                        "modelName", modelCode + " name",
                        "modelType", "DIAGNOSIS",
                        "defaultTimeoutMs", timeoutMs,
                        "ruleFallbackEnabled", true,
                        "description", "B-25 integration model"
                )))
                .header("Authorization", "Bearer " + token.accessToken()));
    }

    private org.springframework.test.web.servlet.ResultActions createVersion(TokenPairResponse token, String modelCode, String versionNo, int timeoutMs) throws Exception {
        return mockMvc.perform(post("/api/models/{modelCode}/versions", modelCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "versionNo", versionNo,
                        "artifactUri", "local://models/" + modelCode.toLowerCase() + "/" + versionNo.toLowerCase(),
                        "featureSchemaVersion", "FEATURE-SCHEMA-B25",
                        "timeoutMs", timeoutMs,
                        "ruleFallbackEnabled", true
                )))
                .header("Authorization", "Bearer " + token.accessToken()));
    }

    private boolean containsModel(JsonNode items, String modelCode) {
        for (JsonNode item : items) {
            if (modelCode.equals(item.path("modelCode").asText())) {
                return true;
            }
        }
        return false;
    }
}
