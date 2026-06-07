package com.guidinglight.nexusquant.research.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.research.application.api.backtest.BacktestConfigApiService;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.marketdata.application.MarketdataDatasetService;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/**
 * BacktestConfigControllerTest 验证回测配置列表/详情查询面与统一错误结构。
 */
class BacktestConfigControllerTest {

    private MockMvc mockMvc;
    private BacktestConfigApiService applicationService;
    private MarketdataDatasetService marketdataDatasetService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        applicationService = mock(BacktestConfigApiService.class);
        marketdataDatasetService = mock(MarketdataDatasetService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BacktestConfigController(applicationService, marketdataDatasetService))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeBacktestConfigListAndDetail() throws Exception {
        BacktestConfig backtestConfig = new BacktestConfig(
                "bcf-1",
                "rcf-1",
                "Demo Backtest",
                "用于联调列表与详情",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{\"mode\":\"bar\"}",
                "{\"benchmark\":\"BTC\"}",
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-31T00:00:00Z\",\"initialCapital\":\"100000\",\"executionSpec\":{\"mode\":\"bar\"}}",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-21T00:00:00Z")
        );
        when(applicationService.list("rcf-1")).thenReturn(List.of(backtestConfig));
        when(applicationService.getByBacktestConfigId("bcf-1")).thenReturn(backtestConfig);

        mockMvc.perform(get("/api/backtest-configs")
                        .param("researchConfigId", "rcf-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-config-list"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-config-list"))
                .andExpect(jsonPath("$[0].backtestConfigId").value("bcf-1"))
                .andExpect(jsonPath("$[0].researchConfigId").value("rcf-1"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].initialCapital").value(100000));

        mockMvc.perform(get("/api/backtest-configs/bcf-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-config-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backtestConfigId").value("bcf-1"))
                .andExpect(jsonPath("$.executionSpec").value("{\"mode\":\"bar\"}"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.configSnapshot").exists());
    }

    @Test
    void shouldReturnEmptyBacktestConfigList() throws Exception {
        when(applicationService.list("rcf-empty")).thenReturn(List.of());

        mockMvc.perform(get("/api/backtest-configs")
                        .param("researchConfigId", "rcf-empty")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-config-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldArchiveBacktestConfig() throws Exception {
        BacktestConfig archived = new BacktestConfig(
                "bcf-1",
                "rcf-1",
                "Demo Backtest",
                "用于联调归档命令",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{\"mode\":\"bar\"}",
                "{\"benchmark\":\"BTC\"}",
                null,
                "{}",
                "{}",
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-31T00:00:00Z\",\"initialCapital\":\"100000\",\"executionSpec\":{\"mode\":\"bar\"}}",
                null,
                "{}",
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-31T00:00:00Z\",\"initialCapital\":\"100000\",\"executionSpec\":{\"mode\":\"bar\"}}",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-22T00:00:00Z"),
                BacktestConfig.STATUS_ARCHIVED,
                Instant.parse("2026-03-22T00:00:00Z"),
                "system",
                "retired from default list"
        );
        when(applicationService.archive("bcf-1", "system", "retired from default list"))
                .thenReturn(archived);

        mockMvc.perform(post("/api/backtest-configs/bcf-1/archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiveReason\":\"retired from default list\"}")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-config-archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backtestConfigId").value("bcf-1"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedBy").value("system"))
                .andExpect(jsonPath("$.archiveReason").value("retired from default list"));
    }

    @Test
    void shouldBindDatasetToBacktestConfig() throws Exception {
        String datasetId = "33333333-3333-3333-3333-333333333333";
        String datasetSnapshot = """
                {"datasetId":"33333333-3333-3333-3333-333333333333","provider":"db","resourcePath":"marketdata_bars","exchangeCode":"BINANCE","marketType":"SPOT","symbol":"BTC-USDT","interval":"1m"}
                """;
        BacktestConfig bound = new BacktestConfig(
                "bcf-1",
                "rcf-1",
                "Demo Backtest",
                "用于联调 dataset 绑定",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{\"mode\":\"bar\"}",
                "{\"benchmark\":\"BTC\"}",
                datasetId,
                datasetSnapshot,
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-31T00:00:00Z\",\"initialCapital\":\"100000\",\"executionSpec\":{\"mode\":\"bar\"}}",
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-03-21T00:00:00Z")
        );
        when(marketdataDatasetService.buildDatasetSnapshot(java.util.UUID.fromString(datasetId)))
                .thenReturn(datasetSnapshot);
        when(applicationService.bindDataset("bcf-1", datasetId, datasetSnapshot)).thenReturn(bound);

        mockMvc.perform(patch("/api/backtest-configs/bcf-1/dataset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":\"" + datasetId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backtestConfigId").value("bcf-1"))
                .andExpect(jsonPath("$.datasetId").value(datasetId))
                .andExpect(jsonPath("$.datasetSnapshotJson").value(datasetSnapshot));
    }

    @Test
    void shouldReturnNotFoundWhenBacktestConfigMissing() throws Exception {
        when(applicationService.getByBacktestConfigId("bcf-missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "backtest config not found: bcf-missing"));

        mockMvc.perform(get("/api/backtest-configs/bcf-missing")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-config-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("backtest config not found: bcf-missing"))
                .andExpect(jsonPath("$.traceId").value("trc-backtest-config-404"));
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




