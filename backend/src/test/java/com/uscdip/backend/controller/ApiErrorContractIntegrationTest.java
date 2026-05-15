package com.uscdip.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateTraceIdWhenHeaderMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/menu-boundaries"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.traceId").isString())
                .andReturn();

        String traceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(traceId).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("\"traceId\":\"" + traceId + "\"");
    }

    @Test
    void shouldPassThroughTraceIdFromRequestHeader() throws Exception {
        String requestTraceId = "a07traceid0001";
        MvcResult result = mockMvc.perform(get("/api/menu-boundaries").header("X-Trace-Id", requestTraceId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", requestTraceId))
                .andExpect(jsonPath("$.traceId").value(requestTraceId))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"traceId\":\"" + requestTraceId + "\"");
    }

    @Test
    void shouldReturnInvalidParameterWhenGisConvertPayloadIsEmpty() throws Exception {
        mockMvc.perform(post("/api/gis/convert")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void shouldReturnResourceNotFoundForUnknownPlatform() throws Exception {
        mockMvc.perform(get("/api/platforms/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void shouldReturnDataScopeEmptyWhenOutOfRegionRequested() throws Exception {
        mockMvc.perform(post("/api/authz/check")
                        .contentType("application/json")
                        .content("{" +
                                "\"userId\":\"U-DISPATCH-001\"," +
                                "\"entryPermission\":\"ENTRY:EMGC\"," +
                                "\"menuPermission\":\"MENU:WORKORDER:READ\"," +
                                "\"regionId\":\"REGION-SH\"," +
                                "\"topic\":\"region.REGION-SH.alerts.critical\"," +
                                "\"dataView\":\"AGGREGATED\"" +
                                "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DATA_SCOPE_EMPTY"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void shouldListStandardErrorCodes() throws Exception {
        mockMvc.perform(get("/api/error-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.code=='INVALID_PARAMETER')]").exists())
                .andExpect(jsonPath("$.traceId").isString());
    }
}
