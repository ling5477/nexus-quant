package com.guidinglight.nexusquant.app.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.research.application.api.backtest.BacktestConfigApiService;
import com.guidinglight.nexusquant.research.application.eval.api.BacktestRunApiService;
import com.guidinglight.nexusquant.research.application.api.ResearchConfigApiService;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.research.api.web.BacktestConfigController;
import com.guidinglight.nexusquant.research.api.web.BacktestRunController;
import com.guidinglight.nexusquant.research.api.web.ResearchConfigController;
import com.guidinglight.nexusquant.app.config.auth.SecurityConfiguration;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.marketdata.application.MarketdataDatasetService;
import com.guidinglight.nexusquant.observability.config.ObservabilityAutoConfiguration;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ResearchBacktestQueryControllerLocalTest 验证 Step 6 新增查询接口在 Spring WebMvc 上下文中的装配与访问控制。
 */
@ActiveProfiles("local")
@Import({
        ApiExceptionHandler.class,
        ObservabilityAutoConfiguration.class,
        SecurityConfiguration.class,
        ResearchBacktestQueryControllerLocalTest.QueryControllerConfiguration.class
})
@TestPropertySource(properties = {
        "nq.security.issuer=nexus-quant-test",
        "nq.security.secret=test-change-me-test-change-me-123456",
        "nq.security.access-token-ttl=PT30M",
        "nq.security.users[0].username=viewer",
        "nq.security.users[0].password-hash=$2a$10$vwD9EsN2B2E/O6DkKhg60ewPvhbERSY9QNGkW1yocbpRk2BOzsO5S",
        "nq.security.users[0].roles[0]=VIEWER",
        "nq.security.users[0].enabled=true"
})
@WebMvcTest(useDefaultFilters = false)
class ResearchBacktestQueryControllerLocalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResearchConfigApiService researchConfigApiService;
    @MockitoBean
    private BacktestConfigApiService backtestConfigApiService;
    @MockitoBean
    private BacktestRunApiService backtestRunApiService;
    @MockitoBean
    private MarketdataDatasetService marketdataDatasetService;
    @MockitoBean
    private AuthUserRepository authUserRepository;

    @Test
    @WithMockUser(username = "local-viewer", roles = {"VIEWER"})
    void shouldExposeResearchAndBacktestQueriesForAuthenticatedViewer() throws Exception {
        when(researchConfigApiService.list(null)).thenReturn(List.of(new ResearchConfig(
                "rcf-1",
                "str-1",
                "{\"strategyType\":\"BUY_AND_HOLD_FIXTURE\"}",
                "Demo Research",
                "用于本地装配验证",
                "{}",
                "{}",
                "{\"symbol\":\"BTCUSDT\",\"interval\":\"1m\"}",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-21T00:00:00Z")
        )));
        when(backtestConfigApiService.list(null)).thenReturn(List.of(new BacktestConfig(
                "bcf-1",
                "rcf-1",
                "Demo Backtest",
                "用于本地装配验证",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{\"mode\":\"bar\"}",
                "{}",
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-31T00:00:00Z\",\"initialCapital\":\"100000\",\"executionSpec\":{\"mode\":\"bar\"}}",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-21T00:00:00Z")
        )));
        when(backtestRunApiService.list(null, null)).thenReturn(List.of(new BacktestRun(
                "brn-1",
                "bcf-1",
                "rcf-1",
                "str-1",
                "{\"strategyType\":\"BUY_AND_HOLD_FIXTURE\"}",
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-31T00:00:00Z\"}",
                BacktestRunStatus.SUCCEEDED,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                null,
                null,
                "{\"orderCount\":1,\"tradeCount\":1,\"finalEquity\":\"100001.000000000000000000\"}",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z")
        )));
        when(backtestRunApiService.findEvaluationOrNull("brn-1")).thenReturn(null);
        when(backtestRunApiService.findPublishOrNull("brn-1")).thenReturn(null);

        mockMvc.perform(get("/api/research-configs")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-research"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-research"))
                .andExpect(jsonPath("$[0].researchConfigId").value("rcf-1"));

        mockMvc.perform(get("/api/backtest-configs")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-backtest-config"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-backtest-config"))
                .andExpect(jsonPath("$[0].backtestConfigId").value("bcf-1"));

        mockMvc.perform(get("/api/backtest-runs")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-backtest-run"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-backtest-run"))
                .andExpect(jsonPath("$[0].backtestRunId").value("brn-1"));
    }

    @Test
    void shouldRejectUnauthenticatedResearchAndBacktestQueries() throws Exception {
        mockMvc.perform(get("/api/research-configs")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-research-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-research-401"));

        mockMvc.perform(get("/api/backtest-configs")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-backtest-config-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-backtest-config-401"));

        mockMvc.perform(get("/api/backtest-runs")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-backtest-run-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-backtest-run-401"));
    }

    @TestConfiguration
    static class QueryControllerConfiguration {

        @Bean
        ResearchConfigController researchConfigController(ResearchConfigApiService researchConfigApiService) {
            return new ResearchConfigController(researchConfigApiService);
        }

        @Bean
        BacktestConfigController backtestConfigController(
                BacktestConfigApiService backtestConfigApiService,
                MarketdataDatasetService marketdataDatasetService
        ) {
            return new BacktestConfigController(backtestConfigApiService, marketdataDatasetService);
        }

        @Bean
        BacktestRunController backtestRunController(BacktestRunApiService backtestRunApiService) {
            return new BacktestRunController(backtestRunApiService);
        }
    }
}



