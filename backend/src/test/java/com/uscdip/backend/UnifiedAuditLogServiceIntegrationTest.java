package com.uscdip.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uscdip.backend.dto.TokenPairResponse;
import com.uscdip.backend.entity.EmergencyAccountEntity;
import com.uscdip.backend.repository.AuditLogRepository;
import com.uscdip.backend.repository.EmergencyAccountRepository;
import com.uscdip.backend.service.LocalTokenService;
import com.uscdip.backend.service.SecurityAuditService;
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
class UnifiedAuditLogServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private SecurityAuditService securityAuditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EmergencyAccountRepository emergencyAccountRepository;

    @Test
    void platformAdminCanQueryAuditLogsAndRegularUserIsDenied() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        TokenPairResponse inspectorToken = localTokenService.issueForUser("U-INSPECT-001", "127.0.0.1", "JUnit");

        MvcResult result = mockMvc.perform(get("/api/audit-logs")
                        .param("eventCategory", "MODEL")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventCategory").value("MODEL"))
                .andReturn();

        String auditId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("auditId").asText();
        mockMvc.perform(get("/api/audit-logs/{auditId}", auditId)
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditId").value(auditId));

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + inspectorToken.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityAuditServiceMirrorsEventsToUnifiedAuditLog() {
        long before = auditLogRepository.count();

        securityAuditService.log(
                SecurityAuditService.EVENT_PERMISSION_REVOKED,
                "U-DISPATCH-001",
                "TOKEN-B27-TEST",
                "SESSION-B27-TEST",
                SecurityAuditService.OUTCOME_SUCCESS,
                "B-27 test permission convergence",
                "127.0.0.1",
                "JUnit"
        );

        Assertions.assertEquals(before + 1, auditLogRepository.count());
        Assertions.assertTrue(auditLogRepository.findAllByOrderByEventTimeDescAuditIdDesc().stream()
                .anyMatch(audit -> "TOKEN_PERMISSION_REVOKED".equals(audit.getEventType())
                        && "AUTH".equals(audit.getEventCategory())
                        && "U-DISPATCH-001".equals(audit.getActorUserId())));
    }

    @Test
    void workOrderDispatchWritesUnifiedAuditLog() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        MvcResult createResult = mockMvc.perform(post("/api/workorders")
                        .header("Authorization", "Bearer " + adminToken.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "incidentId", "INC-ALERT-SEED-001",
                                "workOrderType", "INCIDENT_RESPONSE",
                                "priority", "HIGH",
                                "description", "B-27 audit dispatch test"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String workOrderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("workOrderId").asText();

        mockMvc.perform(post("/api/workorders/{workOrderId}/dispatch", workOrderId)
                        .header("Authorization", "Bearer " + adminToken.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assigneeUserId", "U-INSPECT-001",
                                "assignee", "zhangsan",
                                "reason", "B-27 audit dispatch"
                        ))))
                .andExpect(status().isOk());

        Assertions.assertTrue(auditLogRepository.findAllByOrderByEventTimeDescAuditIdDesc().stream()
                .anyMatch(audit -> "WORK_ORDER_DISPATCHED".equals(audit.getEventType())
                        && workOrderId.equals(audit.getObjectId())
                        && "WORK_ORDER".equals(audit.getEventCategory())));
    }

    @Test
    void modelRollbackWritesUnifiedAuditLog() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        String modelCode = "AUDIT_ROLLBACK_MODEL";

        mockMvc.perform(post("/api/models/register")
                        .header("Authorization", "Bearer " + adminToken.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "modelCode", modelCode,
                                "modelName", "B-27 Audit Rollback Model",
                                "modelType", "DIAGNOSIS",
                                "defaultTimeoutMs", 1000,
                                "ruleFallbackEnabled", true
                        ))))
                .andExpect(status().isCreated());
        createVersion(adminToken, modelCode, "V1");
        mockMvc.perform(post("/api/models/{modelCode}/versions/{versionNo}/activate", modelCode, "V1")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk());
        createVersion(adminToken, modelCode, "V2");
        mockMvc.perform(post("/api/models/{modelCode}/versions/{versionNo}/activate", modelCode, "V2")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/models/{modelCode}/rollback", modelCode)
                        .header("Authorization", "Bearer " + adminToken.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetVersionNo", "V1",
                                "reason", "B-27 rollback audit"
                        ))))
                .andExpect(status().isOk());

        Assertions.assertTrue(auditLogRepository.findAllByOrderByEventTimeDescAuditIdDesc().stream()
                .anyMatch(audit -> "MODEL_ROLLBACK".equals(audit.getEventType())
                        && modelCode.equals(audit.getObjectId())
                        && "MODEL".equals(audit.getEventCategory())));
    }

    @Test
    void breakGlassProtectedOperationIsReportedSeparately() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");
        EmergencyAccountEntity emergencyAccount = emergencyAccountRepository.findById("EA-B05-ACTIVE-001")
                .orElseThrow();
        TokenPairResponse breakGlassToken = localTokenService.issueForEmergencyAccount(emergencyAccount, "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/workorders")
                        .header("Authorization", "Bearer " + breakGlassToken.accessToken()))
                .andExpect(status().isOk());

        MvcResult reportResult = mockMvc.perform(get("/api/audit-logs/break-glass-report")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventCategory").value("BREAK_GLASS"))
                .andReturn();

        JsonNode items = objectMapper.readTree(reportResult.getResponse().getContentAsString()).path("data").path("items");
        Assertions.assertFalse(items.isEmpty());
        Assertions.assertTrue(auditLogRepository.findAllByOrderByEventTimeDescAuditIdDesc().stream()
                .anyMatch(audit -> "BREAK_GLASS_OPERATION".equals(audit.getEventType())
                        && "EA-B05-ACTIVE-001".equals(audit.getEmergencyAccountId())));
    }

    @Test
    void auditFiltersSupportTraceActorCategoryObjectAndEmergencyOnly() throws Exception {
        TokenPairResponse adminToken = localTokenService.issueForUser("U-ADMIN-001", "127.0.0.1", "JUnit");

        mockMvc.perform(get("/api/audit-logs")
                        .param("traceId", "TRACE-B27-BG-001")
                        .param("actorUserId", "U-B05-COMMAND-001")
                        .param("eventCategory", "BREAK_GLASS")
                        .param("objectType", "HTTP_ENDPOINT")
                        .param("emergencyOnly", "true")
                        .header("Authorization", "Bearer " + adminToken.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].traceId").value("TRACE-B27-BG-001"))
                .andExpect(jsonPath("$.data.items[0].eventCategory").value("BREAK_GLASS"));
    }

    private void createVersion(TokenPairResponse adminToken, String modelCode, String versionNo) throws Exception {
        mockMvc.perform(post("/api/models/{modelCode}/versions", modelCode)
                        .header("Authorization", "Bearer " + adminToken.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "versionNo", versionNo,
                                "artifactUri", "local://models/" + modelCode.toLowerCase() + "/" + versionNo.toLowerCase(),
                                "featureSchemaVersion", "B27-SCHEMA",
                                "timeoutMs", 1000,
                                "ruleFallbackEnabled", true
                        ))))
                .andExpect(status().isCreated());
    }
}
