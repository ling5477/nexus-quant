package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Cause;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Confidence;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.RunDiagnostics;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Severity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * PaperExecutionDiagnosticsAssembler 单测：验证规则化归因主因 / 辅助原因 / 优先级 / 可信度 /
 * 主因分布与 strategy/publish 分组聚合，以及空输入稳定空结构。
 *
 * 输入直接构造 {@link PaperPortfolioSummary.RunRef}（与组合看板同一单 run 事实口径）。
 */
class PaperExecutionDiagnosticsAssemblerTest {

    @Test
    void noOrderRunShouldDiagnoseAsNoOrder() {
        // RUNNING + 有 equity（数据充分）+ 无订单无成交 → 纯 NO_ORDER。
        var diag = single(ref("r-noorder", "RUNNING", "sv-1", "pub-1",
                "100000", "100000", "0", "0", "0",
                false, 0, 0, 0));
        assertEquals(Cause.NO_ORDER, diag.primaryCause());
        assertTrue(diag.secondaryCauses().isEmpty());
        assertEquals(Severity.WARNING, diag.severity());
        assertEquals(Confidence.HIGH, diag.causeConfidence());
        assertTrue(diag.explanation().contains("未产生任何订单"));
    }

    @Test
    void createdNoOrderRunShouldDowngradeConfidenceToMedium() {
        // CREATED 尚未启动：仍是无订单，但可信度降级为 MEDIUM、severity 降为 INFO。
        var diag = single(ref("r-created", "CREATED", "sv-1", "pub-1",
                "100000", "100000", "0", "0", "0",
                false, 0, 0, 0));
        assertEquals(Cause.NO_ORDER, diag.primaryCause());
        assertEquals(Confidence.MEDIUM, diag.causeConfidence());
        assertEquals(Severity.INFO, diag.severity());
        assertTrue(diag.explanation().contains("尚未启动"));
    }

    @Test
    void orderNoFillRunShouldDiagnoseAsOrderNoFill() {
        var diag = single(ref("r-onf", "RUNNING", "sv-1", "pub-1",
                "100000", "100000", "0", "0", "0",
                false, 0, 3, 0));
        assertEquals(Cause.ORDER_NO_FILL, diag.primaryCause());
        assertEquals(Confidence.HIGH, diag.causeConfidence());
        assertEquals(Severity.WARNING, diag.severity());
    }

    @Test
    void filledLossRunShouldDiagnoseAsFilledLoss() {
        // 有成交且当前 PnL 为负 → FILLED_LOSS。
        var diag = single(ref("r-loss", "STOPPED", "sv-1", "pub-1",
                "94000", "100000", "-6000", "-0.06", "-0.06",
                false, 0, 4, 2));
        assertEquals(Cause.FILLED_LOSS, diag.primaryCause());
        assertEquals(Confidence.HIGH, diag.causeConfidence());
        assertEquals(Severity.WARNING, diag.severity());
    }

    @Test
    void riskBlockedRunShouldDiagnoseAsRiskBlocked() {
        var diag = single(ref("r-risk", "RUNNING", "sv-1", "pub-1",
                "100000", "100000", "0", "0", "0",
                true, 2, 1, 0));
        assertEquals(Cause.RISK_BLOCKED, diag.primaryCause());
        assertEquals(Severity.CRITICAL, diag.severity());
        assertEquals(Confidence.HIGH, diag.causeConfidence());
        // 风控拦截优先级高于「有订单无成交」，后者进入辅助原因。
        assertTrue(diag.secondaryCauses().contains(Cause.ORDER_NO_FILL));
    }

    @Test
    void dataInsufficientRunShouldDiagnoseAsDataInsufficient() {
        // 无 equity（currentEquity/initialEquity 为 null）→ DATA_INSUFFICIENT。
        var diag = single(ref("r-di", "RUNNING", "sv-1", "pub-1",
                null, null, null, null, null,
                false, 0, 0, 0));
        assertEquals(Cause.DATA_INSUFFICIENT, diag.primaryCause());
        assertEquals(Confidence.HIGH, diag.causeConfidence());
        assertEquals(Severity.WARNING, diag.severity());
        // RUNNING 且事实不足 → RUNNING_NO_RESULT 作为辅助原因。
        assertTrue(diag.secondaryCauses().contains(Cause.RUNNING_NO_RESULT));
        assertTrue(diag.secondaryCauses().contains(Cause.NO_ORDER));
    }

    @Test
    void highDrawdownRunShouldDiagnoseAsHighDrawdown() {
        // 有成交、非亏损、回撤 -15% ≤ -10% 阈值 → HIGH_DRAWDOWN（排除 HEALTHY）。
        var diag = single(ref("r-dd", "STOPPED", "sv-1", "pub-1",
                "105000", "100000", "5000", "0.05", "-0.15",
                false, 0, 4, 3));
        assertEquals(Cause.HIGH_DRAWDOWN, diag.primaryCause());
        assertEquals(Severity.CRITICAL, diag.severity());
        assertEquals(Confidence.HIGH, diag.causeConfidence());
    }

    @Test
    void failedRunShouldDiagnoseAsFailedRunWithHighestPriority() {
        // FAILED + 风控拦截 + 数据不足 → 主因 FAILED_RUN，辅助按优先级含 DATA_INSUFFICIENT、RISK_BLOCKED。
        var diag = single(ref("r-failed", "FAILED", "sv-1", "pub-1",
                null, null, null, null, null,
                true, 1, 0, 0));
        assertEquals(Cause.FAILED_RUN, diag.primaryCause());
        assertEquals(Severity.CRITICAL, diag.severity());
        assertEquals(List.of(Cause.DATA_INSUFFICIENT, Cause.RISK_BLOCKED, Cause.NO_ORDER), diag.secondaryCauses());
    }

    @Test
    void healthyRunShouldDiagnoseAsHealthy() {
        var diag = single(ref("r-ok", "STOPPED", "sv-1", "pub-1",
                "112000", "100000", "12000", "0.12", "-0.03",
                false, 0, 4, 3));
        assertEquals(Cause.HEALTHY, diag.primaryCause());
        assertEquals(Severity.INFO, diag.severity());
        assertTrue(diag.secondaryCauses().isEmpty());
    }

    @Test
    void multipleCausesShouldOrderSecondaryByPriority() {
        // 风控拦截 + 成交亏损 + 高回撤（非 FAILED、数据充分）→ 主因 RISK_BLOCKED，辅助按优先级 [FILLED_LOSS, HIGH_DRAWDOWN]。
        var diag = single(ref("r-multi", "RUNNING", "sv-1", "pub-1",
                "80000", "100000", "-20000", "-0.2", "-0.25",
                true, 3, 5, 2));
        assertEquals(Cause.RISK_BLOCKED, diag.primaryCause());
        assertEquals(List.of(Cause.FILLED_LOSS, Cause.HIGH_DRAWDOWN), diag.secondaryCauses());
    }

    @Test
    void causeDistributionShouldAggregatePrimaryCausesInPriorityOrder() {
        var diagnostics = PaperExecutionDiagnosticsAssembler.assemble(List.of(
                ref("r1", "FAILED", "sv-1", "pub-1", null, null, null, null, null, false, 0, 0, 0),
                ref("r2", "RUNNING", "sv-1", "pub-1", "100000", "100000", "0", "0", "0", false, 0, 0, 0),
                ref("r3", "RUNNING", "sv-2", "pub-2", "100000", "100000", "0", "0", "0", false, 0, 0, 0),
                ref("r4", "STOPPED", "sv-2", "pub-2", "112000", "100000", "12000", "0.12", "-0.02", false, 0, 4, 3)));

        Map<Cause, Integer> byCause = diagnostics.causeDistribution().stream()
                .collect(Collectors.toMap(
                        PaperExecutionDiagnostics.CauseDistribution::cause,
                        PaperExecutionDiagnostics.CauseDistribution::count));
        assertEquals(1, byCause.get(Cause.FAILED_RUN));
        assertEquals(2, byCause.get(Cause.NO_ORDER));
        assertEquals(1, byCause.get(Cause.HEALTHY));
        // 分布按 Cause 优先级（枚举序）输出：FAILED_RUN 在 NO_ORDER 之前，NO_ORDER 在 HEALTHY 之前。
        List<Cause> order = diagnostics.causeDistribution().stream()
                .map(PaperExecutionDiagnostics.CauseDistribution::cause).toList();
        assertTrue(order.indexOf(Cause.FAILED_RUN) < order.indexOf(Cause.NO_ORDER));
        assertTrue(order.indexOf(Cause.NO_ORDER) < order.indexOf(Cause.HEALTHY));
    }

    @Test
    void overviewShouldCountFactsIndependently() {
        var diagnostics = PaperExecutionDiagnosticsAssembler.assemble(List.of(
                // 成交亏损 + 高回撤：同时计入 filledRunCount / filledLossRunCount / highDrawdownRunCount。
                ref("r1", "STOPPED", "sv-1", "pub-1", "80000", "100000", "-20000", "-0.2", "-0.25", false, 0, 4, 2),
                ref("r2", "RUNNING", "sv-1", "pub-1", "100000", "100000", "0", "0", "0", false, 0, 0, 0),
                ref("r3", "FAILED", "sv-2", "pub-2", null, null, null, null, null, false, 0, 0, 0)));

        var o = diagnostics.overview();
        assertEquals(3, o.totalRuns());
        assertEquals(1, o.failedRunCount());
        assertEquals(1, o.runningRunCount());
        // noOrder 为事实计数：r2（RUNNING 无单）与 r3（FAILED 无单）均无订单无成交，互不排斥 → 2。
        assertEquals(2, o.noOrderRunCount());
        assertEquals(1, o.filledRunCount());
        assertEquals(1, o.filledLossRunCount());
        assertEquals(1, o.highDrawdownRunCount());
        assertEquals(1, o.dataInsufficientRunCount()); // r3 无 equity
    }

    @Test
    void strategyAndPublishDiagnosticsShouldAggregateByKey() {
        var diagnostics = PaperExecutionDiagnosticsAssembler.assemble(List.of(
                ref("r1", "FAILED", "sv-1", "pub-1", null, null, null, null, null, false, 0, 0, 0),
                ref("r2", "RUNNING", "sv-1", "pub-1", "100000", "100000", "0", "0", "0", false, 0, 0, 0),
                ref("r3", "STOPPED", "sv-2", "pub-2", "112000", "100000", "12000", "0.12", "-0.02", false, 0, 4, 3)));

        Map<String, PaperExecutionDiagnostics.GroupDiagnostics> byStrategy = diagnostics.strategyDiagnostics().stream()
                .collect(Collectors.toMap(PaperExecutionDiagnostics.GroupDiagnostics::key, Function.identity()));
        var sv1 = byStrategy.get("sv-1");
        assertEquals(2, sv1.runCount());
        // sv-1 组内最紧急主因 = FAILED_RUN（r1）。
        assertEquals(Cause.FAILED_RUN, sv1.primaryCause());
        assertEquals(Severity.CRITICAL, sv1.severity());
        // noOrder 为事实计数：r1（FAILED 无单）与 r2（RUNNING 无单）均无订单无成交 → 2。
        assertEquals(2, sv1.noOrderCount());
        assertTrue(sv1.topCauses().contains(Cause.FAILED_RUN));
        assertTrue(sv1.topCauses().contains(Cause.NO_ORDER));

        var sv2 = byStrategy.get("sv-2");
        assertEquals(1, sv2.runCount());
        assertEquals(Cause.HEALTHY, sv2.primaryCause());

        // publish 维度同样聚合：pub-1 含 2 run，pub-2 含 1 run。
        Map<String, PaperExecutionDiagnostics.GroupDiagnostics> byPublish = diagnostics.publishDiagnostics().stream()
                .collect(Collectors.toMap(PaperExecutionDiagnostics.GroupDiagnostics::key, Function.identity()));
        assertEquals(2, byPublish.get("pub-1").runCount());
        assertEquals(1, byPublish.get("pub-2").runCount());
    }

    @Test
    void blankKeysShouldFallBackToStableGroupKeys() {
        var diagnostics = PaperExecutionDiagnosticsAssembler.assemble(List.of(
                ref("r1", "RUNNING", null, null, "100000", "100000", "0", "0", "0", false, 0, 0, 0)));
        assertEquals("(未绑定策略版本)", diagnostics.strategyDiagnostics().get(0).key());
        assertEquals("(未知发布)", diagnostics.publishDiagnostics().get(0).key());
    }

    @Test
    void emptyInputShouldReturnStableEmptyStructure() {
        var diagnostics = PaperExecutionDiagnosticsAssembler.assemble(List.of());
        assertEquals(0, diagnostics.overview().totalRuns());
        assertTrue(diagnostics.causeDistribution().isEmpty());
        assertTrue(diagnostics.runDiagnostics().isEmpty());
        assertTrue(diagnostics.strategyDiagnostics().isEmpty());
        assertTrue(diagnostics.publishDiagnostics().isEmpty());

        // null 输入同样稳定（不抛异常）。
        var fromNull = PaperExecutionDiagnosticsAssembler.assemble(null);
        assertEquals(0, fromNull.overview().totalRuns());
        assertTrue(fromNull.runDiagnostics().isEmpty());
    }

    @Test
    void runDiagnosticsExplanationShouldNotContainInvestmentAdvice() {
        // Paper-only 边界：suggestedAction 不得出现「买入/卖出/加仓/减仓/投资建议」等真实交易动作语义。
        var diagnostics = PaperExecutionDiagnosticsAssembler.assemble(List.of(
                ref("r1", "STOPPED", "sv-1", "pub-1", "94000", "100000", "-6000", "-0.06", "-0.06", false, 0, 4, 2)));
        String text = diagnostics.runDiagnostics().get(0).explanation()
                + diagnostics.runDiagnostics().get(0).suggestedAction();
        for (String banned : List.of("买入", "卖出", "加仓", "减仓", "投资建议", "推荐")) {
            assertFalse(text.contains(banned), "诊断文案不得含投资动作语义: " + banned);
        }
    }

    // ---- fixtures ----

    private static RunDiagnostics single(PaperPortfolioSummary.RunRef ref) {
        return PaperExecutionDiagnosticsAssembler.assemble(List.of(ref)).runDiagnostics().get(0);
    }

    /**
     * 构造单 run 事实引用。noOrder/orderNoFill/hasFill 由 orderCount/tradeCount 派生（与组合看板口径一致），
     * 保证三态互斥穷尽。
     */
    private static PaperPortfolioSummary.RunRef ref(
            String runId, String status, String strategyVersionId, String publishId,
            String currentEquity, String initialEquity, String totalPnl, String totalReturn, String maxDrawdown,
            boolean riskBlocked, int openAlertCount, int orderCount, int tradeCount
    ) {
        boolean hasFill = tradeCount > 0;
        boolean orderNoFill = !hasFill && orderCount > 0;
        boolean noOrder = !hasFill && orderCount == 0;
        return new PaperPortfolioSummary.RunRef(
                runId, status, "BTC-USDT", strategyVersionId, publishId,
                dec(currentEquity), dec(initialEquity), dec(totalPnl), dec(totalReturn), dec(maxDrawdown),
                riskBlocked, openAlertCount, orderCount, tradeCount, noOrder, orderNoFill, hasFill,
                Instant.parse("2026-06-02T00:00:00Z"));
    }

    private static BigDecimal dec(String value) {
        return value != null ? new BigDecimal(value) : null;
    }
}
