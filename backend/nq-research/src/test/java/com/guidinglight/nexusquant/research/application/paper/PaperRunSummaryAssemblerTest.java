package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperOrderStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryType;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckSeverity;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class PaperRunSummaryAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-06-24T02:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-06-24T01:00:00Z");
    private static final Instant STARTED = Instant.parse("2026-06-24T01:01:00Z");
    private static final Instant STOPPED = Instant.parse("2026-06-24T01:02:00Z");

    @Test
    void shouldAggregateHealthyStoppedRun() {
        PaperTradingRun run = run(PaperTradingRunStatus.STOPPED, STARTED, STOPPED);

        PaperRunSummary summary = PaperRunSummaryAssembler.assemble(
                run,
                List.of(order(PaperOrderStatus.FILLED)),
                List.of(trade()),
                List.of(position("125", "-65")),
                List.of(risk(RiskCheckStatus.PASSED, RiskCheckSeverity.LOW)),
                List.of(equity("125", "-65")),
                List.of(),
                List.of(),
                List.of(),
                NOW
        );

        assertEquals(1, summary.counts().orderCount());
        assertEquals(1, summary.counts().tradeCount());
        assertEquals(1, summary.counts().fillCount());
        assertEquals(1, summary.counts().positionCount());
        assertEquals(0, summary.counts().openAlertCount());
        assertEquals(0, summary.counts().recoveryEventCount());

        assertEquals("STOPPED", summary.resultReview().finalStatus());
        assertEquals("1 分钟 0 秒", summary.resultReview().runtimeDurationText());
        assertEquals(0, summary.resultReview().netPnl().compareTo(new BigDecimal("60")));
        assertEquals("通过", summary.resultReview().riskResult());
        assertEquals("正常完成", summary.resultReview().conclusion());
        assertEquals("info", summary.resultReview().conclusionLevel());

        // 无异常 → 仅 HEALTHY。
        assertEquals(1, summary.diagnoses().size());
        assertEquals("HEALTHY", summary.diagnoses().get(0).type());
        assertEquals("INFO", summary.diagnoses().get(0).severity());

        Set<String> titles = timelineTitles(summary);
        assertTrue(titles.containsAll(Set.of(
                "Paper run created", "Paper run started", "Paper run stopped",
                "最新订单状态事件", "最新成交事件", "最新持仓更新时间",
                "最新净 PnL / equity snapshot", "最新风控检查结果")));

        assertNotNull(summary.latest().order());
        assertNotNull(summary.latest().trade());
        assertNotNull(summary.latest().riskResult());
    }

    @Test
    void shouldReturnStableStructureForEmptyFacts() {
        PaperTradingRun run = run(PaperTradingRunStatus.STOPPED, null, STOPPED);

        PaperRunSummary summary = PaperRunSummaryAssembler.assemble(
                run, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), NOW);

        assertEquals(0, summary.counts().orderCount());
        assertEquals(0, summary.counts().fillCount());
        assertNull(summary.resultReview().netPnl());
        assertEquals("无交易", summary.resultReview().conclusion());

        Set<String> types = diagnosisTypes(summary);
        assertTrue(types.contains("NO_ORDER"));
        assertTrue(types.contains("DATA_INSUFFICIENT"));
        assertFalse(types.contains("HEALTHY"));

        assertNull(summary.latest().order());
        Set<String> titles = timelineTitles(summary);
        assertTrue(titles.contains("Paper run created"));
        assertTrue(titles.contains("Paper run stopped"));
        assertFalse(titles.contains("最新订单状态事件"));
    }

    @Test
    void shouldDiagnoseRiskBlocked() {
        PaperTradingRun run = run(PaperTradingRunStatus.STOPPED, STARTED, STOPPED);

        PaperRunSummary summary = PaperRunSummaryAssembler.assemble(
                run,
                List.of(order(PaperOrderStatus.REJECTED)),
                List.of(),
                List.of(),
                List.of(risk(RiskCheckStatus.REJECTED, RiskCheckSeverity.HIGH)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                NOW
        );

        assertEquals("拦截", summary.resultReview().riskResult());
        assertEquals("风控拦截", summary.resultReview().conclusion());
        assertEquals("danger", summary.resultReview().conclusionLevel());

        Set<String> types = diagnosisTypes(summary);
        assertTrue(types.contains("RISK_BLOCKED"));
        assertEquals("BLOCKING", summary.diagnoses().get(0).severity());
        // 风控拦截时抑制 NO_FILL 噪声。
        assertFalse(types.contains("NO_FILL"));
    }

    @Test
    void shouldDiagnoseAlertAndRecovery() {
        PaperTradingRun run = run(PaperTradingRunStatus.STOPPED, STARTED, STOPPED);

        PaperRunSummary summary = PaperRunSummaryAssembler.assemble(
                run,
                List.of(order(PaperOrderStatus.FILLED)),
                List.of(trade()),
                List.of(position("125", "-65")),
                List.of(risk(RiskCheckStatus.PASSED, RiskCheckSeverity.LOW)),
                List.of(equity("125", "-65")),
                List.of(),
                List.of(openAlert()),
                List.of(recoveryEvent()),
                NOW
        );

        assertEquals(1, summary.counts().openAlertCount());
        assertEquals(1, summary.counts().recoveryEventCount());

        Set<String> types = diagnosisTypes(summary);
        assertTrue(types.contains("ALERT_PRESENT"));
        assertTrue(types.contains("RECOVERY_PRESENT"));
        assertFalse(types.contains("HEALTHY"));

        assertNotNull(summary.latest().alert());
        assertNotNull(summary.latest().recoveryEvent());
    }

    @Test
    void shouldDiagnoseNegativePnl() {
        PaperTradingRun run = run(PaperTradingRunStatus.STOPPED, STARTED, STOPPED);

        PaperRunSummary summary = PaperRunSummaryAssembler.assemble(
                run,
                List.of(order(PaperOrderStatus.FILLED)),
                List.of(trade()),
                List.of(),
                List.of(risk(RiskCheckStatus.PASSED, RiskCheckSeverity.LOW)),
                List.of(equity("-50", "-100")),
                List.of(),
                List.of(),
                List.of(),
                NOW
        );

        assertEquals(0, summary.resultReview().netPnl().compareTo(new BigDecimal("-150")));
        assertTrue(diagnosisTypes(summary).contains("PNL_NEGATIVE"));
    }

    @Test
    void shouldDiagnoseNoFillWhenOrdersWithoutTrades() {
        PaperTradingRun run = run(PaperTradingRunStatus.STOPPED, STARTED, STOPPED);

        PaperRunSummary summary = PaperRunSummaryAssembler.assemble(
                run,
                List.of(order(PaperOrderStatus.CREATED)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                NOW
        );

        Set<String> types = diagnosisTypes(summary);
        assertTrue(types.contains("NO_FILL"));
        assertTrue(types.contains("DATA_INSUFFICIENT"));
        assertFalse(types.contains("NO_ORDER"));
    }

    // ---- helpers ----

    private static Set<String> diagnosisTypes(PaperRunSummary summary) {
        return summary.diagnoses().stream().map(PaperRunSummary.Diagnosis::type).collect(Collectors.toSet());
    }

    private static Set<String> timelineTitles(PaperRunSummary summary) {
        return summary.timeline().stream().map(PaperRunSummary.TimelineEntry::title).collect(Collectors.toSet());
    }

    private static PaperTradingRun run(PaperTradingRunStatus status, Instant started, Instant stopped) {
        return new PaperTradingRun(
                "ptr-1", "pub-1", "sv-1", status, "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m",
                started, stopped, "{}", "{}", "{}", "{}", "{}", "tester", CREATED, STOPPED);
    }

    private static PaperTradingOrder order(PaperOrderStatus status) {
        return new PaperTradingOrder(
                "po-1", "ptr-1", "BTC-USDT", "BUY", "MARKET",
                new BigDecimal("1"), new BigDecimal("65000"), status, null, "{}",
                CREATED, Instant.parse("2026-06-24T01:01:30Z"));
    }

    private static PaperTradingTrade trade() {
        return new PaperTradingTrade(
                "pt-1", "po-1", "ptr-1", "BTC-USDT", "BUY",
                new BigDecimal("1"), new BigDecimal("65000"), new BigDecimal("65"),
                Instant.parse("2026-06-24T01:01:31Z"), Instant.parse("2026-06-24T01:01:31Z"));
    }

    private static PaperTradingPosition position(String unrealized, String realized) {
        return new PaperTradingPosition(
                "pos-1", "ptr-1", "BTC-USDT", new BigDecimal("1"), new BigDecimal("65000"),
                new BigDecimal(unrealized), new BigDecimal(realized),
                Instant.parse("2026-06-24T01:02:00Z"), Instant.parse("2026-06-24T01:01:31Z"));
    }

    private static EquityCurveSnapshot equity(String unrealized, String realized) {
        return new EquityCurveSnapshot(
                "eq-1", "ptr-1", Instant.parse("2026-06-24T01:02:30Z"),
                new BigDecimal("100060"), new BigDecimal("35000"), new BigDecimal("65060"),
                new BigDecimal(unrealized), new BigDecimal(realized), new BigDecimal("0"),
                "TEST", Instant.parse("2026-06-24T01:02:30Z"));
    }

    private static PaperRiskCheckResult risk(RiskCheckStatus status, RiskCheckSeverity severity) {
        return new PaperRiskCheckResult(
                "rk-1", "ptr-1", "BASIC_HEALTH_CHECK", status, severity, "msg", "{}", "{}",
                Instant.parse("2026-06-24T01:03:00Z"));
    }

    private static PaperRunAlert openAlert() {
        return new PaperRunAlert(
                "al-1", "ptr-1", "RISK", PaperRunAlertSeverity.MEDIUM, PaperRunAlertStatus.OPEN,
                "Drawdown breach", "risk gate rejected order", "RISK_ENGINE", "{}",
                null, null, null,
                Instant.parse("2026-06-24T01:03:10Z"), Instant.parse("2026-06-24T01:03:10Z"));
    }

    private static PaperRunRecoveryEvent recoveryEvent() {
        return new PaperRunRecoveryEvent(
                "rec-1", "ptr-1", PaperRunRecoveryType.MANUAL_RECOVER, PaperRunRecoveryStatus.FAILED,
                "auto recover failed", "{}", "{}",
                Instant.parse("2026-06-24T01:04:00Z"), Instant.parse("2026-06-24T01:04:05Z"),
                Instant.parse("2026-06-24T01:04:00Z"));
    }
}
