package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.research.application.paper.assembler.PaperStrategyEvaluationAssembler;
import com.guidinglight.nexusquant.research.application.paper.service.PaperLegacyAnalysisService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperPortfolioService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperStrategyEvaluationService;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRiskCheckResultRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingOrderRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingRunRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.publish.port.BacktestEvaluationQueryPort;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** 窗口身份与来源是三类分析的共同输入，不能独立重选或把多出的一条当作完整统计。 */
class PaperLegacyAnalysisServiceTest {
    private final PaperTradingRunRepository runs = mock(PaperTradingRunRepository.class);
    private final BacktestPublishRecordRepository publishes =
            mock(BacktestPublishRecordRepository.class);
    private final EquityCurveSnapshotRepository equity = mock(EquityCurveSnapshotRepository.class);
    private final PaperRunDailyReportRepository reports = mock(PaperRunDailyReportRepository.class);
    private final PaperRiskCheckResultRepository risks = mock(PaperRiskCheckResultRepository.class);
    private final PaperRunAlertRepository alerts = mock(PaperRunAlertRepository.class);
    private final PaperTradingOrderRepository orders = mock(PaperTradingOrderRepository.class);
    private final PaperTradingTradeRepository trades = mock(PaperTradingTradeRepository.class);
    private final PaperPortfolioService portfolio =
            new PaperPortfolioService(
                    runs, equity, reports, risks, alerts, orders, trades, publishes);

    @Test
    void allSectionsUseOneBoundedWindowAndNeverScanEveryPublish() {
        var rows = IntStream.range(0, 201).mapToObj(this::legacy).toList();
        when(runs.listLegacyForAnalysis(7, 201)).thenReturn(rows);
        var evaluation = mock(PaperStrategyEvaluationService.class);
        when(evaluation.evaluateLegacy(eq(7L), anyList()))
                .thenAnswer(
                        call ->
                                PaperStrategyEvaluationAssembler.assemble(
                                        call.getArgument(1), Map.of()));
        var result = new PaperLegacyAnalysisService(portfolio, evaluation).read(7);
        assertEquals("LEGACY_PAPER", result.source());
        assertEquals(200, result.maxRuns());
        assertEquals(200, result.includedRuns());
        assertTrue(result.hasMore());
        assertEquals(200, result.portfolio().overview().totalRuns());
        assertEquals(200, result.runs().size());
        assertFalse(result.runs().stream().anyMatch(row -> row.paperRunId().equals("legacy-200")));
        assertEquals(200, result.diagnostics().overview().totalRuns());
        assertEquals(200, result.reviews().overview().totalRuns());
        assertFalse(
                result.diagnostics().runDiagnostics().stream()
                        .anyMatch(row -> row.paperRunId().equals("legacy-200")));
        assertEquals(result.generatedAt(), result.reviews().overview().generatedAt());
        verify(runs).listLegacyForAnalysis(7, 201);
        verify(runs, never()).list(null, null);
        verify(publishes).listVisibleByIds(eq(7L), anyCollection());
        verify(publishes, never()).listAll();
        verify(equity)
                .listByRunIds(argThat(ids -> ids.size() == 200 && !ids.contains("legacy-200")));
    }

    @Test
    void emptyLegacyWindowDoesNotGenerateFakeHealthyOrProfitableRuns() {
        when(runs.listLegacyForAnalysis(7, 201)).thenReturn(List.of());
        var evaluation = mock(PaperStrategyEvaluationService.class);
        when(evaluation.evaluateLegacy(7, List.of()))
                .thenReturn(PaperStrategyEvaluationAssembler.assemble(List.of(), Map.of()));
        var snapshot = new PaperLegacyAnalysisService(portfolio, evaluation).read(7);
        assertFalse(snapshot.hasMore());
        assertEquals(0, snapshot.includedRuns());
        assertTrue(snapshot.diagnostics().runDiagnostics().isEmpty());
        assertTrue(snapshot.reviews().runReviews().isEmpty());
        assertEquals(0, snapshot.portfolio().overview().totalRuns());
        assertTrue(snapshot.runs().isEmpty());
        assertTrue(snapshot.portfolio().portfolioCurve().points().isEmpty());
    }

    /** 即使交易对文字相同，旧权益也没有计价单位；不能以名称推断后合计或制造资金加权收益。 */
    @Test
    void unrecordedUnitsKeepIndividualFactsButNeverProduceCrossRunValuations() {
        when(runs.listLegacyForAnalysis(7, 201)).thenReturn(List.of(legacy(1), legacy(2)));
        Instant time = Instant.parse("2026-10-01T00:00:00Z");
        when(equity.listByRunIds(anyCollection()))
                .thenReturn(
                        Map.of(
                                "legacy-1",
                                        List.of(
                                                new EquityCurveSnapshot(
                                                        "unit-a",
                                                        "legacy-1",
                                                        time,
                                                        new BigDecimal("110"),
                                                        new BigDecimal("110"),
                                                        BigDecimal.ZERO,
                                                        BigDecimal.ZERO,
                                                        new BigDecimal("10"),
                                                        BigDecimal.ZERO,
                                                        "SYNTHETIC",
                                                        time)),
                                "legacy-2",
                                        List.of(
                                                new EquityCurveSnapshot(
                                                        "unit-b",
                                                        "legacy-2",
                                                        time,
                                                        new BigDecimal("500"),
                                                        new BigDecimal("500"),
                                                        BigDecimal.ZERO,
                                                        BigDecimal.ZERO,
                                                        new BigDecimal("-500"),
                                                        BigDecimal.ZERO,
                                                        "SYNTHETIC",
                                                        time))));
        var evaluation =
                new PaperStrategyEvaluationService(
                        portfolio, publishes, mock(BacktestEvaluationQueryPort.class));
        var snapshot = new PaperLegacyAnalysisService(portfolio, evaluation).read(7);
        assertEquals(2, snapshot.includedRuns());
        assertEquals(2, snapshot.portfolio().overview().returnEligibleRunCount());
        assertEquals(new BigDecimal("110"), snapshot.runs().getFirst().currentEquity());
        assertEquals(new BigDecimal("0.100000"), snapshot.runs().getFirst().totalReturn());
        assertEquals(new BigDecimal("-0.500000"), snapshot.runs().getLast().totalReturn());
        assertNull(snapshot.portfolio().overview().totalCurrentEquity());
        assertNull(snapshot.portfolio().overview().totalInitialEquity());
        assertNull(snapshot.portfolio().overview().totalPnl());
        assertNull(snapshot.portfolio().overview().totalReturn());
        assertTrue(snapshot.portfolio().portfolioCurve().points().isEmpty());
        assertNull(snapshot.portfolio().highlights().topWinner());
        for (var group : snapshot.portfolio().strategyGroups()) {
            assertNull(group.currentEquity());
            assertNull(group.totalPnl());
            assertNull(group.totalReturn());
            assertEquals(2, group.runCount());
        }
        for (var group : snapshot.evaluations().strategyEvaluations()) {
            assertNull(group.currentEquity());
            assertNull(group.initialEquity());
            assertNull(group.totalPnl());
            assertNull(group.totalReturn());
            assertNull(group.backtestDeviation().paperReturn());
            assertNull(group.backtestDeviation().returnDeviation());
            assertEquals(new BigDecimal("-0.200000"), group.averageReturn());
            assertEquals(1, group.winRunCount());
            assertEquals(1, group.lossRunCount());
        }
        verify(runs).listLegacyForAnalysis(7, 201);
        verify(equity).listByRunIds(anyCollection());
    }

    @Test
    void canonicalOrLiveRowsFailBeforeAnyEconomicChildRead() {
        var canonical = legacy(1, 44L, "SIM");
        when(runs.listLegacyForAnalysis(7, 201)).thenReturn(List.of(canonical));
        assertThrows(IllegalStateException.class, () -> portfolio.listLegacyRunRefs(7));
        var live = legacy(2, null, "LIVE");
        when(runs.listLegacyForAnalysis(7, 201)).thenReturn(List.of(live));
        assertThrows(IllegalStateException.class, () -> portfolio.listLegacyRunRefs(7));
        verifyNoInteractions(equity, reports, risks, alerts, orders, trades, publishes);
    }

    @Test
    void invalidOwnerDoesNotReadAnyRepository() {
        assertThrows(IllegalArgumentException.class, () -> portfolio.listLegacyRunRefs(0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PaperLegacyAnalysisService(
                                        portfolio, mock(PaperStrategyEvaluationService.class))
                                .read(-1));
        verifyNoInteractions(runs, publishes);
    }

    private PaperTradingRun legacy(int index) {
        return legacy(index, null, "SIM");
    }

    private PaperTradingRun legacy(int index, Long canonicalAccount, String environment) {
        Instant time = Instant.parse("2026-10-01T00:00:00Z");
        return new PaperTradingRun(
                "legacy-" + index,
                "legacy-publish",
                "legacy-version",
                PaperTradingRunStatus.CREATED,
                environment,
                "BINANCE",
                "SPOT",
                "BTC-USDT",
                "1h",
                null,
                null,
                "{}",
                "{}",
                "{}",
                "{}",
                "{}",
                "synthetic",
                time,
                time,
                canonicalAccount);
    }
}
