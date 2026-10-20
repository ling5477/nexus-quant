package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.research.application.paper.assembler.PaperRunSummaryAssembler;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunMonitorService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunRecoveryService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunSummaryService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingMonitorService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.paper.PaperOrderStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunCanonicalEconomicsQuery;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunCanonicalFactsRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingOrderRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingPositionRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingRunRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 运行存在性先于事实源选择；canonical与旧Paper分别保持各自的事实身份。 */
class PaperRunCanonicalProjectionTest {
    private static final Instant TIME = Instant.parse("2026-09-29T00:00:00Z");

    @Test
    void routesStrategySimToCanonicalAndPreservesLegacy() {
        var runs = mock(PaperTradingRunRepository.class);
        var oldOrders = mock(PaperTradingOrderRepository.class);
        var oldTrades = mock(PaperTradingTradeRepository.class);
        var oldPositions = mock(PaperTradingPositionRepository.class);
        var canonical = mock(PaperRunCanonicalFactsRepository.class);
        var service =
                new PaperTradingRunService(
                        runs,
                        oldOrders,
                        oldTrades,
                        oldPositions,
                        mock(BacktestPublishRecordRepository.class),
                        mock(BacktestRunRepository.class),
                        Clock.systemUTC(),
                        canonical);
        var run = run("sim");
        // 正式读服务已先校验运行；夹具必须建立合法运行，不能靠放宽生产校验绕过此边界。
        when(runs.findById("sim")).thenReturn(Optional.of(run));
        when(runs.findById("legacy")).thenReturn(Optional.of(run("legacy")));
        when(runs.findById("empty")).thenReturn(Optional.of(run("empty")));
        var order =
                new PaperTradingOrder(
                        "ord-1",
                        "sim",
                        "BTC-USDT",
                        "BUY",
                        "MARKET",
                        new BigDecimal("1.25"),
                        new BigDecimal("100"),
                        PaperOrderStatus.FILLED,
                        null,
                        null,
                        TIME,
                        TIME);
        var trade =
                new PaperTradingTrade(
                        "trd-1",
                        "ord-1",
                        "sim",
                        "BTC-USDT",
                        "BUY",
                        new BigDecimal("1.25"),
                        new BigDecimal("100"),
                        new BigDecimal("0.1"),
                        TIME,
                        TIME);
        var position =
                new PaperTradingPosition(
                        "canonical-position-41",
                        "sim",
                        "BTC-USDT",
                        new BigDecimal("1.25"),
                        new BigDecimal("100"),
                        null,
                        null,
                        TIME,
                        TIME);
        when(canonical.isStrategySim("sim")).thenReturn(true);
        when(canonical.orders("sim")).thenReturn(List.of(order));
        when(canonical.trades("sim")).thenReturn(List.of(trade));
        when(canonical.positions("sim")).thenReturn(List.of(position));
        assertEquals(List.of(order), service.listOrders("sim"));
        assertEquals(List.of(trade), service.listTrades("sim"));
        assertEquals(List.of(position), service.listPositions("sim"));
        verifyNoInteractions(oldOrders, oldTrades, oldPositions);

        var legacyOrder =
                new PaperTradingOrder(
                        "legacy-order",
                        "legacy",
                        "BTC-USDT",
                        "BUY",
                        "MARKET",
                        order.quantity(),
                        order.price(),
                        PaperOrderStatus.FILLED,
                        null,
                        null,
                        TIME,
                        TIME);
        var legacyTrade =
                new PaperTradingTrade(
                        "legacy-trade",
                        "legacy-order",
                        "legacy",
                        "BTC-USDT",
                        "BUY",
                        trade.quantity(),
                        trade.price(),
                        trade.fee(),
                        TIME,
                        TIME);
        var legacyPosition =
                new PaperTradingPosition(
                        "legacy-position",
                        "legacy",
                        "BTC-USDT",
                        position.quantity(),
                        position.avgPrice(),
                        null,
                        null,
                        TIME,
                        TIME);
        when(oldOrders.listByRunId("legacy")).thenReturn(List.of(legacyOrder));
        when(oldTrades.listByRunId("legacy")).thenReturn(List.of(legacyTrade));
        when(oldPositions.listByRunId("legacy")).thenReturn(List.of(legacyPosition));
        assertEquals(List.of(legacyOrder), service.listOrders("legacy"));
        assertEquals(List.of(legacyTrade), service.listTrades("legacy"));
        assertEquals(List.of(legacyPosition), service.listPositions("legacy"));
        assertTrue(service.listOrders("empty").isEmpty());

        var summary =
                PaperRunSummaryAssembler.assemble(
                        run,
                        service.listOrders("sim"),
                        service.listTrades("sim"),
                        service.listPositions("sim"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        TIME);
        assertEquals(1, summary.counts().orderCount());
        assertEquals(1, summary.counts().tradeCount());
        assertEquals(1, summary.counts().fillCount());
        assertEquals(1, summary.counts().positionCount());
        var withCanonicalPnl =
                PaperRunSummaryAssembler.assemble(
                        run,
                        service.listOrders("sim"),
                        service.listTrades("sim"),
                        service.listPositions("sim"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        TIME,
                        new BigDecimal("12.50"));
        assertEquals(
                0, withCanonicalPnl.resultReview().netPnl().compareTo(new BigDecimal("12.50")));

        var economics = mock(PaperRunCanonicalEconomicsQuery.class);
        when(economics.pnl("sim")).thenReturn(new BigDecimal("12.50"));
        var summaryService =
                new PaperRunSummaryService(
                        service,
                        mock(PaperTradingMonitorService.class),
                        mock(PaperRunMonitorService.class),
                        mock(PaperRunRecoveryService.class),
                        Clock.systemUTC(),
                        economics);
        assertEquals(
                0,
                summaryService
                        .summarize("sim")
                        .resultReview()
                        .netPnl()
                        .compareTo(new BigDecimal("12.50")));
    }

    @Test
    void missingRunCannotReadCanonicalOrLegacyFacts() {
        var runs = mock(PaperTradingRunRepository.class);
        var oldOrders = mock(PaperTradingOrderRepository.class);
        var oldTrades = mock(PaperTradingTradeRepository.class);
        var oldPositions = mock(PaperTradingPositionRepository.class);
        var canonical = mock(PaperRunCanonicalFactsRepository.class);
        var service =
                new PaperTradingRunService(
                        runs,
                        oldOrders,
                        oldTrades,
                        oldPositions,
                        mock(BacktestPublishRecordRepository.class),
                        mock(BacktestRunRepository.class),
                        Clock.systemUTC(),
                        canonical);
        assertThrows(IllegalArgumentException.class, () -> service.listOrders("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.listTrades("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.listPositions("missing"));
        verifyNoInteractions(canonical, oldOrders, oldTrades, oldPositions);
    }

    private static PaperTradingRun run(String id) {
        return new PaperTradingRun(
                id,
                "publish",
                "version",
                PaperTradingRunStatus.RUNNING,
                "SIM",
                "OKX",
                "SPOT",
                "BTC-USDT",
                "1h",
                TIME,
                null,
                "{}",
                "{}",
                "{}",
                "{}",
                "{}",
                "test",
                TIME,
                TIME);
    }
}
