package com.guidinglight.nexusquant.research.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.research.application.api.ResearchConfigApiService;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/**
 * ResearchConfigControllerTest 验证研究配置列表/详情查询面与统一错误结构。
 */
class ResearchConfigControllerTest {

    private MockMvc mockMvc;
    private ResearchConfigApiService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = mock(ResearchConfigApiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ResearchConfigController(applicationService))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeResearchConfigListAndDetail() throws Exception {
        ResearchConfig researchConfig = new ResearchConfig(
                "rcf-1",
                "str-1",
                "{\"strategyType\":\"BUY_AND_HOLD_FIXTURE\"}",
                "Demo Research",
                "用于联调列表与详情",
                "{\"type\":\"object\"}",
                "{\"window\":20}",
                "{\"provider\":\"fixture\",\"symbol\":\"BTCUSDT\",\"interval\":\"1m\"}",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-21T00:00:00Z")
        );
        when(applicationService.list("str-1")).thenReturn(List.of(researchConfig));
        when(applicationService.getByResearchConfigId("rcf-1")).thenReturn(researchConfig);

        mockMvc.perform(get("/api/research-configs")
                        .param("sourceStrategyId", "str-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-research-list"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-research-list"))
                .andExpect(jsonPath("$[0].researchConfigId").value("rcf-1"))
                .andExpect(jsonPath("$[0].sourceStrategyId").value("str-1"))
                .andExpect(jsonPath("$[0].datasetSpec").value("{\"provider\":\"fixture\",\"symbol\":\"BTCUSDT\",\"interval\":\"1m\"}"));

        mockMvc.perform(get("/api/research-configs/rcf-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-research-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.researchConfigId").value("rcf-1"))
                .andExpect(jsonPath("$.strategySnapshot").value("{\"strategyType\":\"BUY_AND_HOLD_FIXTURE\"}"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void shouldReturnEmptyResearchConfigList() throws Exception {
        when(applicationService.list("str-empty")).thenReturn(List.of());

        mockMvc.perform(get("/api/research-configs")
                        .param("sourceStrategyId", "str-empty")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-research-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldReturnNotFoundWhenResearchConfigMissing() throws Exception {
        when(applicationService.getByResearchConfigId("rcf-missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "research config not found: rcf-missing"));

        mockMvc.perform(get("/api/research-configs/rcf-missing")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-research-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("research config not found: rcf-missing"))
                .andExpect(jsonPath("$.traceId").value("trc-research-404"));
    }

    private static final class TestTraceIdFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, java.io.IOException {
            String incoming = request.getHeader(TraceIdContext.TRACE_ID_HEADER);
            String traceId = TraceIdContext.putOrCreate(incoming);
            request.setAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE, traceId);
            response.setHeader(TraceIdContext.TRACE_ID_HEADER, traceId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                TraceIdContext.clear();
            }
        }
    }
}




