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
import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview;

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
 * PaperAutoReviewControllerTest 验证 GateK K4 只读自动复盘端点的路由、映射、Paper-only safety 与脱敏不变量。
 * standalone MockMvc + mock {@link PaperTradingApiService}，证明 GET 返回 200、映射各复盘块、声明规则化 / 非投资建议、
 * 不泄漏 secret，且只调用一次只读 {@code autoReviews()}。
 */
class PaperAutoReviewControllerTest {

    private static final Set<String> SECRET_TOKENS =
            Set.of("secret", "apikey", "api_key", "token", "signature", "passphrase");

    private PaperTradingApiService apiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        apiService = mock(PaperTradingApiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaperAutoReviewController(apiService))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnAutoReviewWithPaperOnlySafety() throws Exception {
        when(apiService.autoReviews()).thenReturn(sampleReview());

        MvcResult result = mockMvc.perform(get("/api/paper-trading/auto-reviews")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-auto-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totalRuns").value(1))
                .andExpect(jsonPath("$.overview.topIssueCause").value("RISK_BLOCKED"))
                .andExpect(jsonPath("$.portfolioReview.headline").value("Paper 组合存在关键执行问题，需优先处理高风险 run。"))
                .andExpect(jsonPath("$.runReviews[0].paperRunId").value("run-1"))
                .andExpect(jsonPath("$.runReviews[0].primaryCause").value("RISK_BLOCKED"))
                .andExpect(jsonPath("$.strategyReviews[0].ratingLabel").value("HIGH_RISK"))
                .andExpect(jsonPath("$.publishReviews[0].publishId").value("pub-1"))
                .andExpect(jsonPath("$.issueClusters[0].clusterKey").value("RISK_BLOCKED"))
                .andExpect(jsonPath("$.safety.paperOnly").value(true))
                .andExpect(jsonPath("$.safety.rulesBased").value(true))
                .andExpect(jsonPath("$.safety.noInvestmentAdvice").value(true))
                .andExpect(jsonPath("$.safety.noLiveTrading").value(true))
                .andExpect(jsonPath("$.safety.noAiRuntime").value(true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("\"noLiveTrading\":false"), "live must not be enabled: " + body);
        String lowerBody = body.toLowerCase();
        for (String token : SECRET_TOKENS) {
            assertFalse(lowerBody.contains(token), "response must not leak '" + token + "': " + body);
        }

        verify(apiService, times(1)).autoReviews();
        verifyNoMoreInteractions(apiService);
    }

    @Test
    void shouldReturnStableEmptyReview() throws Exception {
        when(apiService.autoReviews()).thenReturn(emptyReview());

        mockMvc.perform(get("/api/paper-trading/auto-reviews")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-auto-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totalRuns").value(0))
                .andExpect(jsonPath("$.portfolioReview.headline").value("暂无足够 Paper 事实生成复盘。"))
                .andExpect(jsonPath("$.runReviews").isEmpty())
                .andExpect(jsonPath("$.safety.paperOnly").value(true));

        verify(apiService, times(1)).autoReviews();
        verifyNoMoreInteractions(apiService);
    }

    private static PaperAutoReview sampleReview() {
        var overview = new PaperAutoReview.Overview(
                1, 1, 1, 0, 1, 0, 1, 1, "RISK_BLOCKED", "RISK", Instant.parse("2026-06-26T00:00:00Z"));
        var portfolio = new PaperAutoReview.PortfolioReview(
                "Paper 组合存在关键执行问题，需优先处理高风险 run。", "组合摘要",
                List.of("发现 1"), List.of("风险 1"), List.of(), List.of(), List.of(),
                List.of("检查风控阈值、仓位限制与风险阈值。"),
                List.of("结论基于 SIM/Paper 模拟执行事实，不代表 LIVE 或真实交易表现。"));
        var run = new PaperAutoReview.RunReview(
                "run-1", "sv-1", "pub-1", "STOPPED", "RISK_BLOCKED", "CRITICAL", "HIGH",
                new BigDecimal("-2000"), new BigDecimal("-0.02"), new BigDecimal("-0.05"),
                "风控拦截", "风控阻断或高风险结果出现。",
                List.of("状态=STOPPED"), List.of("风控规则可能触发"),
                List.of("检查风控规则"), List.of("RISK_BLOCKED"));
        var strategy = new PaperAutoReview.StrategyReview(
                "sv-1", "HIGH_RISK", 35, "MEDIUM", "RISK", "高风险", "存在较高回撤",
                List.of(), List.of("主要短板: 风险评分偏低"), List.of("HIGH_DRAWDOWN"), List.of("检查风控阈值与仓位限制"));
        var publish = new PaperAutoReview.PublishReview(
                "pub-1", "sv-1", "HIGH_RISK", 35, "MEDIUM", "RISK", "高风险", "存在较高回撤",
                List.of(), List.of("主要短板: 风险评分偏低"), List.of("HIGH_DRAWDOWN"), List.of("检查风控阈值与仓位限制"));
        var cluster = new PaperAutoReview.IssueCluster(
                "RISK_BLOCKED", "RISK_BLOCKED", "CRITICAL", 1,
                List.of("run-1"), List.of("sv-1"), List.of("pub-1"),
                "多个 Paper run 被风控阻断或判为高风险。", "检查风控规则、仓位限制与风险阈值。");
        return new PaperAutoReview(
                overview, portfolio, List.of(run), List.of(strategy), List.of(publish), List.of(cluster));
    }

    private static PaperAutoReview emptyReview() {
        return new PaperAutoReview(
                new PaperAutoReview.Overview(0, 0, 0, 0, 0, 0, 0, 0, null, null, Instant.parse("2026-06-26T00:00:00Z")),
                new PaperAutoReview.PortfolioReview(
                        "暂无足够 Paper 事实生成复盘。", "无数据",
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of("结论基于 SIM/Paper 模拟执行事实，不代表 LIVE 或真实交易表现。")),
                List.of(), List.of(), List.of(), List.of());
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
