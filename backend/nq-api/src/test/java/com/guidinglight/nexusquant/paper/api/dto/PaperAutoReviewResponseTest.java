package com.guidinglight.nexusquant.paper.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PaperAutoReviewResponse.from() 映射单测：字段全量映射、Paper-only safety（5 个布尔均 true）、
 * 空结构稳定映射，以及响应不泄漏 secret / credential、不含真实投资建议语义。
 */
class PaperAutoReviewResponseTest {

    private static final List<String> SECRET_TOKENS =
            List.of("secret", "apikey", "api_key", "token", "signature", "passphrase", "private_key", "mnemonic");

    private static final List<String> INVESTMENT_VERBS =
            List.of("买入", "卖出", "加仓", "减仓", "做多", "做空", "实盘");

    @Test
    void fromShouldMapAllFieldsAndDeclarePaperOnlySafety() {
        var overview = new PaperAutoReview.Overview(
                4, 3, 3, 1, 1, 2, 2, 2, "RISK_BLOCKED", "RISK", Instant.parse("2026-06-26T00:00:00Z"));
        var portfolio = new PaperAutoReview.PortfolioReview(
                "Paper 组合存在关键执行问题，需优先处理高风险 run。", "组合摘要",
                List.of("发现 1"), List.of("风险 1"), List.of("执行 1"),
                List.of("策略 1"), List.of("偏差 1"), List.of("检查风控阈值、仓位限制与风险阈值。"),
                List.of("结论基于 SIM/Paper 模拟执行事实，不代表 LIVE 或真实交易表现。"));
        var run = new PaperAutoReview.RunReview(
                "run-1", "sv-1", "pub-1", "STOPPED", "RISK_BLOCKED", "CRITICAL", "HIGH",
                new BigDecimal("-2000"), new BigDecimal("-0.02"), new BigDecimal("-0.05"),
                "风控拦截：执行被风控阻断或判为高风险", "风控阻断或高风险结果出现。",
                List.of("状态=STOPPED", "订单数=4"), List.of("风控规则可能触发"),
                List.of("检查风控规则", "检查仓位限制"), List.of("RISK_BLOCKED", "CRITICAL"));
        var strategy = new PaperAutoReview.StrategyReview(
                "sv-1", "HIGH_RISK", 35, "MEDIUM", "RISK",
                "高风险：存在回撤/风控/失败问题", "该策略版本存在较高回撤...",
                List.of(), List.of("主要短板: 风险评分偏低"), List.of("HIGH_DRAWDOWN"),
                List.of("检查风控阈值与仓位限制"));
        var publish = new PaperAutoReview.PublishReview(
                "pub-1", "sv-1", "HIGH_RISK", 35, "MEDIUM", "RISK",
                "高风险：存在回撤/风控/失败问题", "该策略版本存在较高回撤...",
                List.of(), List.of("主要短板: 风险评分偏低"), List.of("HIGH_DRAWDOWN"),
                List.of("检查风控阈值与仓位限制"));
        var cluster = new PaperAutoReview.IssueCluster(
                "RISK_BLOCKED", "RISK_BLOCKED", "CRITICAL", 1,
                List.of("run-1"), List.of("sv-1"), List.of("pub-1"),
                "多个 Paper run 被风控阻断或判为高风险。", "检查风控规则、仓位限制与风险阈值。");

        var review = new PaperAutoReview(
                overview, portfolio, List.of(run), List.of(strategy), List.of(publish), List.of(cluster));

        var response = PaperAutoReviewResponse.from(review);

        assertEquals(4, response.overview().totalRuns());
        assertEquals("RISK_BLOCKED", response.overview().topIssueCause());
        assertEquals("RISK", response.overview().topWeakness());
        assertEquals("Paper 组合存在关键执行问题，需优先处理高风险 run。", response.portfolioReview().headline());

        var rr = response.runReviews().get(0);
        assertEquals("run-1", rr.paperRunId());
        assertEquals("RISK_BLOCKED", rr.primaryCause());
        assertEquals("CRITICAL", rr.severity());
        assertTrue(rr.suggestedActions().contains("检查风控规则"));

        var sr = response.strategyReviews().get(0);
        assertEquals("HIGH_RISK", sr.ratingLabel());
        assertEquals(35, sr.compositeScore());
        assertEquals("RISK", sr.primaryWeakness());

        var pr = response.publishReviews().get(0);
        assertEquals("pub-1", pr.publishId());
        assertEquals("sv-1", pr.strategyVersionId());

        var ic = response.issueClusters().get(0);
        assertEquals("RISK_BLOCKED", ic.clusterKey());
        assertEquals(1, ic.count());

        // safety：5 个布尔均 true。
        assertTrue(response.safety().paperOnly());
        assertTrue(response.safety().rulesBased());
        assertTrue(response.safety().noInvestmentAdvice());
        assertTrue(response.safety().noLiveTrading());
        assertTrue(response.safety().noAiRuntime());
        assertTrue(response.safety().message().contains("不构成真实投资建议"));

        assertNoSecretsNoInvestmentAdvice(response);
    }

    @Test
    void fromShouldMapEmptyReviewStably() {
        var review = new PaperAutoReview(
                new PaperAutoReview.Overview(0, 0, 0, 0, 0, 0, 0, 0, null, null, Instant.parse("2026-06-26T00:00:00Z")),
                new PaperAutoReview.PortfolioReview(
                        "暂无足够 Paper 事实生成复盘。", "当前没有可用的 bounded Paper run 与策略评估事实，无法生成自动复盘。",
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of("结论基于 SIM/Paper 模拟执行事实，不代表 LIVE 或真实交易表现。")),
                List.of(), List.of(), List.of(), List.of());

        var response = PaperAutoReviewResponse.from(review);

        assertEquals(0, response.overview().totalRuns());
        org.junit.jupiter.api.Assertions.assertNull(response.overview().topIssueCause());
        assertTrue(response.runReviews().isEmpty());
        assertTrue(response.strategyReviews().isEmpty());
        assertTrue(response.publishReviews().isEmpty());
        assertTrue(response.issueClusters().isEmpty());
        assertTrue(response.safety().paperOnly());
        assertEquals("暂无足够 Paper 事实生成复盘。", response.portfolioReview().headline());
        assertNoSecretsNoInvestmentAdvice(response);
    }

    private static void assertNoSecretsNoInvestmentAdvice(PaperAutoReviewResponse response) {
        String text = response.toString().toLowerCase();
        for (String token : SECRET_TOKENS) {
            assertFalse(text.contains(token), "response must not leak '" + token + "'");
        }
        String raw = response.toString();
        for (String verb : INVESTMENT_VERBS) {
            assertFalse(raw.contains(verb), "response must not contain investment verb '" + verb + "'");
        }
    }
}
