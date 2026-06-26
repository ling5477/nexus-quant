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
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Cause;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Confidence;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Severity;

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
 * PaperExecutionDiagnosticsControllerTest 验证 GateK K1 只读诊断端点的路由与映射不变量。
 * <p>
 * standalone MockMvc 装配 controller + mock {@link PaperTradingApiService}，证明
 * {@code GET /api/paper-trading/execution-diagnostics} 返回 200、映射诊断字段、声明 Paper-only safety、
 * 不泄漏 secret，且只调用一次只读 {@code executionDiagnostics()}（不触发其他写动作或外部调用）。
 */
class PaperExecutionDiagnosticsControllerTest {

    private static final Set<String> SECRET_TOKENS =
            Set.of("secret", "apikey", "api_key", "token", "signature", "passphrase");

    private PaperTradingApiService apiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        apiService = mock(PaperTradingApiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaperExecutionDiagnosticsController(apiService))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnDiagnosticsWithPaperOnlySafety() throws Exception {
        when(apiService.executionDiagnostics()).thenReturn(sampleDiagnostics());

        MvcResult result = mockMvc.perform(get("/api/paper-trading/execution-diagnostics")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-exec-diag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totalRuns").value(1))
                .andExpect(jsonPath("$.overview.riskBlockedRunCount").value(1))
                .andExpect(jsonPath("$.causeDistribution[0].cause").value("RISK_BLOCKED"))
                .andExpect(jsonPath("$.runDiagnostics[0].paperRunId").value("run-1"))
                .andExpect(jsonPath("$.runDiagnostics[0].primaryCause").value("RISK_BLOCKED"))
                .andExpect(jsonPath("$.runDiagnostics[0].secondaryCauses[0]").value("FILLED_LOSS"))
                .andExpect(jsonPath("$.runDiagnostics[0].causeConfidence").value("HIGH"))
                .andExpect(jsonPath("$.strategyDiagnostics[0].key").value("sv-1"))
                .andExpect(jsonPath("$.publishDiagnostics[0].key").value("pub-1"))
                // Paper-only safety：环境 SIM/PAPER、LIVE 未开启、未触达真实交易所。
                .andExpect(jsonPath("$.safety.environment").value("SIM/PAPER"))
                .andExpect(jsonPath("$.safety.liveEnabled").value(false))
                .andExpect(jsonPath("$.safety.realExchangeTouched").value(false))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // 诊断结论必须声明不构成投资建议，且不得泄漏任何凭证 token。
        assertFalse(body.contains("\"liveEnabled\":true"), "live must not be enabled: " + body);
        String lowerBody = body.toLowerCase();
        for (String token : SECRET_TOKENS) {
            assertFalse(lowerBody.contains(token), "response must not leak '" + token + "': " + body);
        }

        // 只读：只调用一次诊断聚合，不触发其他 api service 动作（无写动作 / 外部调用）。
        verify(apiService, times(1)).executionDiagnostics();
        verifyNoMoreInteractions(apiService);
    }

    private static PaperExecutionDiagnostics sampleDiagnostics() {
        var run = new PaperExecutionDiagnostics.RunDiagnostics(
                "run-1", "sv-1", "pub-1", "STOPPED", 4, 2,
                new BigDecimal("94000"), new BigDecimal("100000"), new BigDecimal("-6000"),
                new BigDecimal("-0.06"), new BigDecimal("-0.06"), true, 1,
                Cause.RISK_BLOCKED, List.of(Cause.FILLED_LOSS),
                Severity.CRITICAL, Confidence.HIGH,
                "风控规则阻断执行或判定为高风险结果。", "检查最近一次风控检查结果与触发规则。",
                Instant.parse("2026-06-02T00:00:00Z"));
        var group = new PaperExecutionDiagnostics.GroupDiagnostics(
                "sv-1", 1, Cause.RISK_BLOCKED, List.of(Cause.RISK_BLOCKED),
                0, 0, 1, 1, 0, 0, Severity.CRITICAL, Confidence.HIGH);
        var publishGroup = new PaperExecutionDiagnostics.GroupDiagnostics(
                "pub-1", 1, Cause.RISK_BLOCKED, List.of(Cause.RISK_BLOCKED),
                0, 0, 1, 1, 0, 0, Severity.CRITICAL, Confidence.HIGH);
        var distribution = new PaperExecutionDiagnostics.CauseDistribution(
                Cause.RISK_BLOCKED, 1, Severity.CRITICAL, Confidence.HIGH, "风控拦截 run：执行被风控阻断或判为高风险。");
        return new PaperExecutionDiagnostics(
                new PaperExecutionDiagnostics.Overview(1, 0, 0, 1, 1, 1, 0, 0, 0, 0),
                List.of(distribution), List.of(run), List.of(group), List.of(publishGroup));
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
