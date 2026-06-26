package com.guidinglight.nexusquant.paper.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.BacktestDeviation;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.DeviationLevel;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.EvaluationConfidence;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.RatingLabel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PaperStrategyEvaluationControllerTest 验证 GateK K3 只读评估端点的路由、映射、Paper-only safety 与脱敏不变量。
 * standalone MockMvc + mock {@link PaperTradingApiService}，证明 GET 返回 200、映射评估字段、声明非真实投资评级、
 * 不泄漏 secret，且只调用一次只读 {@code strategyEvaluations()}。
 */
class PaperStrategyEvaluationControllerTest {

    private static final Set<String> SECRET_TOKENS =
            Set.of("secret", "apikey", "api_key", "token", "signature", "passphrase");

    private PaperTradingApiService apiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        apiService = mock(PaperTradingApiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaperStrategyEvaluationController(apiService))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnEvaluationWithPaperOnlySafety() throws Exception {
        when(apiService.strategyEvaluations()).thenReturn(sampleEvaluation());

        MvcResult result = mockMvc.perform(get("/api/paper-trading/strategy-evaluations")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-strat-eval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.strategyCount").value(1))
                .andExpect(jsonPath("$.overview.topCompositeScore").value(80))
                .andExpect(jsonPath("$.strategyEvaluations[0].strategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.strategyEvaluations[0].ratingLabel").value("STRONG_PAPER_PERFORMER"))
                .andExpect(jsonPath("$.strategyEvaluations[0].evaluationConfidence").value("HIGH"))
                .andExpect(jsonPath("$.strategyEvaluations[0].compositeScore").value(80))
                .andExpect(jsonPath("$.strategyEvaluations[0].backtestDeviation.deviationLevel").value("LOW"))
                .andExpect(jsonPath("$.publishEvaluations[0].publishId").value("pub-1"))
                .andExpect(jsonPath("$.rankings.topCompositeStrategies[0]").value("sv-1"))
                .andExpect(jsonPath("$.safety.environment").value("SIM/PAPER"))
                .andExpect(jsonPath("$.safety.liveEnabled").value(false))
                .andExpect(jsonPath("$.safety.realExchangeTouched").value(false))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("\"liveEnabled\":true"), "live must not be enabled: " + body);
        String lowerBody = body.toLowerCase();
        for (String token : SECRET_TOKENS) {
            assertFalse(lowerBody.contains(token), "response must not leak '" + token + "': " + body);
        }

        verify(apiService, times(1)).strategyEvaluations();
        verifyNoMoreInteractions(apiService);
    }

    private static PaperStrategyEvaluation sampleEvaluation() {
        var deviation = new BacktestDeviation(
                new BigDecimal("0.1"), new BigDecimal("0.12"), new BigDecimal("0.02"),
                new BigDecimal("-0.03"), new BigDecimal("-0.03"), new BigDecimal("0"),
                DeviationLevel.LOW, "偏差较小");
        var strategy = new PaperStrategyEvaluation.StrategyEvaluation(
                "sv-1", 6, 6, 1, Instant.parse("2026-06-02T00:00:00Z"),
                new BigDecimal("660000"), new BigDecimal("600000"), new BigDecimal("60000"),
                new BigDecimal("0.12"), new BigDecimal("-0.03"),
                6, 0, new BigDecimal("1"), new BigDecimal("0.12"), new BigDecimal("-0.03"),
                0, 0, 0, 0, 6, 0, 0,
                70, 92, 80, 100, 96, 80,
                RatingLabel.STRONG_PAPER_PERFORMER, EvaluationConfidence.HIGH, "SAMPLE",
                List.of(), deviation);
        var publish = new PaperStrategyEvaluation.PublishEvaluation(
                "pub-1", "sv-1", 6, 6, Instant.parse("2026-06-02T00:00:00Z"),
                new BigDecimal("60000"), new BigDecimal("0.12"), new BigDecimal("-0.03"), new BigDecimal("1"),
                70, 92, 80, 100, 96, 80, RatingLabel.STRONG_PAPER_PERFORMER, EvaluationConfidence.HIGH,
                List.of(), deviation);
        return new PaperStrategyEvaluation(
                new PaperStrategyEvaluation.Overview(1, 1, 6, 6, 0, 1, 0, 0, 0, 80, 80),
                List.of(strategy), List.of(publish),
                new PaperStrategyEvaluation.Rankings(
                        List.of("sv-1"), List.of("sv-1"), List.of("sv-1"), List.of("sv-1"),
                        List.of(), List.of(), List.of()));
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
