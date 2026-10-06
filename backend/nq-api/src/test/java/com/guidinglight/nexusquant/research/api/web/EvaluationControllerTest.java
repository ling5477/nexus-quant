package com.guidinglight.nexusquant.research.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.guidinglight.nexusquant.research.application.eval.api.BacktestRunApiService;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationStatus;
import com.guidinglight.nexusquant.research.domain.eval.ResearchValidity;
import com.guidinglight.nexusquant.research.domain.backtest.ResearchRunFacts;
import java.math.BigDecimal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * EvaluationControllerTest 固化评估列表的配置范围，防止 query 参数被 API 层忽略后跨配置串数据。
 */
class EvaluationControllerTest {
    @Test
    void typedResearchResponseExposesAvailableAndMissingStates() throws Exception {
        BacktestEvaluationReport report = mock(BacktestEvaluationReport.class);
        when(report.evalReportId()).thenReturn("eval");
        when(report.backtestRunId()).thenReturn("run");
        when(report.evaluationStatus()).thenReturn(EvaluationStatus.SUCCEEDED);
        var segment = new ResearchValidity.Segment("AVAILABLE", "2026-01-01T00:00:00Z", "2026-01-01T00:06:59Z",
                7, new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("0.01"), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, 1, new BigDecimal("0.1"), new BigDecimal("0.1"));
        var benchmark = new ResearchRunFacts.Benchmark("NOT_AVAILABLE", "MIN_NOTIONAL", "BUY_AND_HOLD",
                "MARK_TO_MARKET", segment.startTime(), segment.endTime(), null, null, null,
                new BigDecimal("100"), null, null, null, null, null);
        when(report.researchValidity()).thenReturn(new ResearchValidity("AVAILABLE", null, ResearchValidity.SPLIT_POLICY,
                "CONTINUOUS_RUN_PREVIOUS_BAR_EQUITY", null, null, segment, segment, segment, benchmark, null));
        when(applicationService.getEvaluationById("eval")).thenReturn(report);
        mockMvc.perform(get("/api/evaluations/eval")).andExpect(status().isOk())
                .andExpect(jsonPath("$.researchValidity.inSample.barCount").value(7))
                .andExpect(jsonPath("$.researchValidity.inSample.strategyReturn").value(0.01))
                .andExpect(jsonPath("$.researchValidity.benchmark.status").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.researchValidity.benchmark.benchmarkReturn").isEmpty());
        when(report.researchValidity()).thenReturn(ResearchValidity.unavailable("LEGACY_REPORT_WITHOUT_RESEARCH_VALIDITY"));
        when(applicationService.listEvaluations(null, null)).thenReturn(List.of(report));
        mockMvc.perform(get("/api/evaluations")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].researchValidity.validationStatus").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$[0].researchValidity.outOfSample").isEmpty());
    }

    private MockMvc mockMvc;
    private BacktestRunApiService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = mock(BacktestRunApiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EvaluationController(applicationService)).build();
    }

    @Test
    void shouldForwardEvaluationFilters() throws Exception {
        when(applicationService.listEvaluations("rcf-1", "bcf-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/evaluations")
                        .queryParam("researchConfigId", "rcf-1")
                        .queryParam("backtestConfigId", "bcf-1"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(applicationService).listEvaluations("rcf-1", "bcf-1");
    }
}
