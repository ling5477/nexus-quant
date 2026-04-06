package com.guidinglight.nexusquant.research.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.research.api.application.backtest.BacktestRunApiService;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.research.domain.backtest.SimOrder;
import com.guidinglight.nexusquant.research.domain.backtest.SimOrderStatus;
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimPosition;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationStatus;
import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/**
 * BacktestRunControllerTest 使用 standalone MockMvc 验证正式回测运行 API 的路由和统一错误结构。
 */
class BacktestRunControllerTest {

    private MockMvc mockMvc;
    private BacktestRunApiService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = mock(BacktestRunApiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BacktestRunController(applicationService))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldStartRunAndExposeStatusAndSummary() throws Exception {
        BacktestRun completedRun = sampleRun(BacktestRunStatus.SUCCEEDED, null);
        when(applicationService.startExecution("brn-1")).thenReturn(completedRun);
        when(applicationService.getByBacktestRunId("brn-1")).thenReturn(completedRun);
        when(applicationService.list("rcf-1", "bcf-1")).thenReturn(List.of(completedRun));
        BacktestEvaluationReport evaluationReport = new BacktestEvaluationReport(
                "eval-1",
                "brn-1",
                EvaluationStatus.SUCCEEDED,
                new BigDecimal("100000"),
                new BigDecimal("99927.76"),
                BigDecimal.ZERO,
                new BigDecimal("99927.76"),
                new BigDecimal("100"),
                BigDecimal.ZERO,
                new BigDecimal("-72.24"),
                new BigDecimal("-0.0007224"),
                new BigDecimal("86.12"),
                new BigDecimal("86.12"),
                2,
                2,
                0,
                1,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "{\"finalEquity\":\"99927.76\"}",
                null,
                null,
                Instant.parse("2025-01-01T00:02:00Z"),
                Instant.parse("2025-01-01T00:02:00Z"),
                Instant.parse("2025-01-01T00:02:00Z")
        );
        BacktestPublishRecord publishRecord = new BacktestPublishRecord(
                "pub-1",
                "brn-1",
                "rcf-1",
                "bcf-1",
                "str-1",
                "eval-1",
                "str-pub-1",
                PublishStatus.SUCCEEDED,
                "Published Demo",
                "{\"sourceBacktestRunId\":\"brn-1\"}",
                "{\"evaluationStatus\":\"SUCCEEDED\"}",
                null,
                null,
                Instant.parse("2025-01-01T00:03:00Z"),
                Instant.parse("2025-01-01T00:03:00Z"),
                Instant.parse("2025-01-01T00:03:00Z")
        );
        when(applicationService.evaluate("brn-1")).thenReturn(evaluationReport);
        when(applicationService.getEvaluation("brn-1")).thenReturn(evaluationReport);
        when(applicationService.findEvaluationOrNull("brn-1")).thenReturn(evaluationReport);
        when(applicationService.publish("brn-1", "Published Demo")).thenReturn(publishRecord);
        when(applicationService.getPublish("brn-1")).thenReturn(publishRecord);
        when(applicationService.findPublishOrNull("brn-1")).thenReturn(publishRecord);
        when(applicationService.listOrders("brn-1")).thenReturn(List.of(new SimOrder(
                "so-1", "brn-1", "BTCUSDT", "BUY", "MARKET",
                new BigDecimal("1"), new BigDecimal("43010"),
                SimOrderStatus.FILLED,
                Instant.parse("2025-01-01T00:00:59Z"),
                Instant.parse("2025-01-01T00:00:59Z"),
                null,
                Instant.parse("2025-01-01T00:00:59Z")
        )));
        when(applicationService.listTrades("brn-1")).thenReturn(List.of(new SimTrade(
                "st-1", "so-1", "brn-1", "BTCUSDT", "BUY",
                new BigDecimal("1"), new BigDecimal("43010"),
                new BigDecimal("43.01"), new BigDecimal("43.01"),
                Instant.parse("2025-01-01T00:00:59Z"),
                Instant.parse("2025-01-01T00:00:59Z"),
                Instant.parse("2025-01-01T00:00:59Z")
        )));
        when(applicationService.listPositions("brn-1")).thenReturn(List.of(new SimPosition(
                "sp-1", "brn-1", "BTCUSDT",
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                Instant.parse("2025-01-01T00:00:59Z"),
                Instant.parse("2025-01-01T00:01:59Z")
        )));
        when(applicationService.listPnlSnapshots("brn-1")).thenReturn(List.of(new SimPnlSnapshot(
                "pnl-1", "brn-1", Instant.parse("2025-01-01T00:01:59Z"),
                new BigDecimal("99927.76"), BigDecimal.ZERO, new BigDecimal("100"),
                BigDecimal.ZERO, new BigDecimal("86.12"), new BigDecimal("86.12"),
                new BigDecimal("99927.76"), new BigDecimal("-72.24"),
                Instant.parse("2025-01-01T00:01:59Z")
        )));

        mockMvc.perform(post("/api/backtest-runs/brn-1/start")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-2"))
                .andExpect(jsonPath("$.backtestRunId").value("brn-1"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.summaryJson").exists());

        mockMvc.perform(get("/api/backtest-runs/brn-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.finishedAt").exists())
                .andExpect(jsonPath("$.evaluationStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.publishStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.finalEquity").value(99927.76));

        mockMvc.perform(get("/api/backtest-runs")
                        .param("researchConfigId", "rcf-1")
                        .param("backtestConfigId", "bcf-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].evaluationStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].publishStatus").value("SUCCEEDED"));

        mockMvc.perform(post("/api/backtest-runs/brn-1/evaluate")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-4")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.netPnl").value(-72.24));

        mockMvc.perform(get("/api/backtest-runs/brn-1/evaluation")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(2));

        mockMvc.perform(post("/api/backtest-runs/brn-1/publish")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Published Demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.targetStrategyDefinitionId").value("str-pub-1"));

        mockMvc.perform(get("/api/backtest-runs/brn-1/publish")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishName").value("Published Demo"));

        mockMvc.perform(get("/api/backtest-runs/brn-1/sim-orders")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].simOrderId").value("so-1"));

        mockMvc.perform(get("/api/backtest-runs/brn-1/sim-trades")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].simTradeId").value("st-1"));

        mockMvc.perform(get("/api/backtest-runs/brn-1/sim-positions")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"));

        mockMvc.perform(get("/api/backtest-runs/brn-1/pnl-snapshots")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatef-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equity").exists());
    }

    @Test
    void shouldReturnUnifiedValidationErrorWhenBacktestConfigIdMissing() throws Exception {
        mockMvc.perform(post("/api/backtest-runs")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-validation"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/backtest-runs"))
                .andExpect(jsonPath("$.traceId").value("trc-backtest-validation"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("backtestConfigId"));
    }

    @Test
    void shouldReturnEmptyListAndNotFoundForMissingRunQueries() throws Exception {
        when(applicationService.list("rcf-empty", null)).thenReturn(List.of());
        when(applicationService.getByBacktestRunId("brn-missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "backtest run not found: brn-missing"));
        when(applicationService.listOrders("brn-missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "backtest run not found: brn-missing"));
        when(applicationService.getEvaluation("brn-missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation report not found: brn-missing"));

        mockMvc.perform(get("/api/backtest-runs")
                        .param("researchConfigId", "rcf-empty")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/backtest-runs/brn-missing")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("backtest run not found: brn-missing"))
                .andExpect(jsonPath("$.traceId").value("trc-backtest-404"));

        mockMvc.perform(get("/api/backtest-runs/brn-missing/sim-orders")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-orders-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("backtest run not found: brn-missing"));

        mockMvc.perform(get("/api/backtest-runs/brn-missing/evaluation")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-backtest-eval-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("evaluation report not found: brn-missing"));
    }

    private BacktestRun sampleRun(BacktestRunStatus status, String failureCode) {
        return new BacktestRun(
                "brn-1",
                "bcf-1",
                "rcf-1",
                "str-1",
                "{\"strategyId\":\"str-1\"}",
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-01T00:05:59Z\"}",
                status,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:05:59Z"),
                failureCode,
                failureCode == null ? null : "sample failure",
                "{\"barCount\":2,\"orderCount\":2,\"tradeCount\":2,\"finalPositionQuantity\":\"0.000000000000000000\",\"finalCashBalance\":\"99927.760000000000000000\",\"finalEquity\":\"99927.760000000000000000\",\"realizedPnl\":\"100.000000000000000000\",\"unrealizedPnl\":\"0E-18\",\"netPnl\":\"-72.240000000000000000\",\"totalFee\":\"86.120000000000000000\",\"totalSlippage\":\"86.120000000000000000\",\"resultStatus\":\"" + status.name() + "\"}",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:05:59Z")
        );
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



