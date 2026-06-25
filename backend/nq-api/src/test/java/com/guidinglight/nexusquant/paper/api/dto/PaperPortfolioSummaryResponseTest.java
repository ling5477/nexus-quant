package com.guidinglight.nexusquant.paper.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.application.paper.PaperPortfolioAssembler;
import com.guidinglight.nexusquant.research.application.paper.PaperPortfolioSummary;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PaperPortfolioSummaryResponse.from 映射单测：验证 Loop-15 新增的组合曲线字段被正确映射，
 * 且旧字段（overview / safety）保持稳定（向后兼容扩展）。
 */
class PaperPortfolioSummaryResponseTest {

    @Test
    void fromShouldMapPortfolioCurveFields() {
        // 单 run：初始 100000 → 110000（最新已实现 10000 回推初始 100000），两点单调上行无回撤。
        var input = new PaperPortfolioAssembler.RunInput(
                run("run-z"),
                List.of(
                        equity("eqz1", "run-z", "2026-06-01T00:00:00Z", "100000", "0", "0"),
                        equity("eqz2", "run-z", "2026-06-02T00:00:00Z", "110000", "10000", "0")),
                List.of(), List.of(), 0, 3, true, true);
        PaperPortfolioSummary summary = PaperPortfolioAssembler.assemble(List.of(input));

        PaperPortfolioSummaryResponse response = PaperPortfolioSummaryResponse.from(summary);

        // 旧字段保持映射（向后兼容）。
        assertEquals(1, response.overview().totalRuns());
        assertEquals("SIM/PAPER", response.safety().environment());

        // 新增组合曲线字段映射。
        var curve = response.portfolioCurve();
        assertNotNull(curve);
        assertEquals(2, curve.pointCount());
        assertEquals(2, curve.points().size());
        assertEquals(0, new BigDecimal("110000").compareTo(curve.latestEquity()));
        assertEquals(0, new BigDecimal("110000").compareTo(curve.peakEquity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(curve.currentDrawdown()));
        assertEquals(0, BigDecimal.ZERO.compareTo(curve.maxDrawdown()));
        assertEquals(1, curve.coverage().comparableRunCount());
        assertEquals(0, curve.coverage().missingEquityRunCount());
        assertEquals(0, curve.coverage().incompletePointCount());

        var firstPoint = curve.points().get(0);
        assertEquals(0, new BigDecimal("100000").compareTo(firstPoint.totalEquity()));
        assertEquals(0, new BigDecimal("100000").compareTo(firstPoint.totalInitialEquity()));
        assertEquals(1, firstPoint.sourceRunCount());
        assertEquals(0, firstPoint.missingRunCount());
    }

    @Test
    void fromShouldMapStableEmptyPortfolioCurveWhenNoRuns() {
        PaperPortfolioSummaryResponse response =
                PaperPortfolioSummaryResponse.from(PaperPortfolioAssembler.assemble(List.of()));

        var curve = response.portfolioCurve();
        assertNotNull(curve);
        assertTrue(curve.points().isEmpty());
        assertEquals(0, curve.pointCount());
        assertNull(curve.latestEquity());
        assertNull(curve.maxDrawdown());
        assertEquals(0, curve.coverage().comparableRunCount());
    }

    private static PaperTradingRun run(String id) {
        return new PaperTradingRun(
                id, "pub-1", "sv-1", PaperTradingRunStatus.STOPPED, "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z"),
                "{}", "{}", "{}", "{}", "{}", "tester",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z"));
    }

    private static EquityCurveSnapshot equity(String id, String runId, String time, String total, String realized, String unrealized) {
        return new EquityCurveSnapshot(
                id, runId, Instant.parse(time), new BigDecimal(total), new BigDecimal(total),
                BigDecimal.ZERO, new BigDecimal(unrealized), new BigDecimal(realized),
                BigDecimal.ZERO, "TEST", Instant.parse(time));
    }
}
