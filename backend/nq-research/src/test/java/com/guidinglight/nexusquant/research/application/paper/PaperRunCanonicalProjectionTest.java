package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.research.application.paper.assembler.PaperRunSummaryAssembler;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunSummaryService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingMonitorService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunMonitorService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunRecoveryService;
import com.guidinglight.nexusquant.research.domain.paper.PaperOrderStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunCanonicalFactsRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunCanonicalEconomicsQuery;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingOrderRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingPositionRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingRunRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaperRunCanonicalProjectionTest {
    private static final Instant TIME = Instant.parse("2026-09-29T00:00:00Z");

    @Test
    void routesStrategySimToCanonicalAndPreservesLegacy() {
        var runs = mock(PaperTradingRunRepository.class);
        var oldOrders = mock(PaperTradingOrderRepository.class);
        var oldTrades = mock(PaperTradingTradeRepository.class);
        var oldPositions = mock(PaperTradingPositionRepository.class);
        var canonical = mock(PaperRunCanonicalFactsRepository.class);
        var service = new PaperTradingRunService(runs, oldOrders, oldTrades, oldPositions,
                mock(BacktestPublishRecordRepository.class), mock(BacktestRunRepository.class),
                Clock.systemUTC(), canonical);
        var order = new PaperTradingOrder("ord-1", "sim", "BTC-USDT", "BUY", "MARKET",
                new BigDecimal("1.25"), new BigDecimal("100"), PaperOrderStatus.FILLED,
                null, null, TIME, TIME);
        var trade = new PaperTradingTrade("trd-1", "ord-1", "sim", "BTC-USDT", "BUY",
                new BigDecimal("1.25"), new BigDecimal("100"), new BigDecimal("0.1"), TIME, TIME);
        var position = new PaperTradingPosition("canonical-position-41", "sim", "BTC-USDT",
                new BigDecimal("1.25"), new BigDecimal("100"), null, null, TIME, TIME);
        when(canonical.isStrategySim("sim")).thenReturn(true);
        when(canonical.orders("sim")).thenReturn(List.of(order));
        when(canonical.trades("sim")).thenReturn(List.of(trade));
        when(canonical.positions("sim")).thenReturn(List.of(position));
        assertEquals(List.of(order), service.listOrders("sim"));
        assertEquals(List.of(trade), service.listTrades("sim"));
        assertEquals(List.of(position), service.listPositions("sim"));
        verifyNoInteractions(oldOrders, oldTrades, oldPositions);

        when(oldOrders.listByRunId("legacy")).thenReturn(List.of(order));
        when(oldTrades.listByRunId("legacy")).thenReturn(List.of(trade));
        when(oldPositions.listByRunId("legacy")).thenReturn(List.of(position));
        assertEquals(List.of(order), service.listOrders("legacy"));
        assertEquals(List.of(trade), service.listTrades("legacy"));
        assertEquals(List.of(position), service.listPositions("legacy"));
        assertTrue(service.listOrders("empty").isEmpty());

        var run = new PaperTradingRun("sim", "publish", "version", PaperTradingRunStatus.RUNNING,
                "SIM", "OKX", "SPOT", "BTC-USDT", "1h", TIME, null,
                "{}", "{}", "{}", "{}", "{}", "test", TIME, TIME);
        var summary = PaperRunSummaryAssembler.assemble(run,
                service.listOrders("sim"), service.listTrades("sim"), service.listPositions("sim"),
                List.of(), List.of(), List.of(), List.of(), List.of(), TIME);
        assertEquals(1, summary.counts().orderCount());
        assertEquals(1, summary.counts().tradeCount());
        assertEquals(1, summary.counts().fillCount());
        assertEquals(1, summary.counts().positionCount());
        var withCanonicalPnl = PaperRunSummaryAssembler.assemble(run,
                service.listOrders("sim"), service.listTrades("sim"), service.listPositions("sim"),
                List.of(), List.of(), List.of(), List.of(), List.of(), TIME,
                new BigDecimal("12.50"));
        assertEquals(0, withCanonicalPnl.resultReview().netPnl().compareTo(new BigDecimal("12.50")));

        var economics = mock(PaperRunCanonicalEconomicsQuery.class);
        when(economics.pnl("sim")).thenReturn(new BigDecimal("12.50"));
        when(runs.findById("sim")).thenReturn(Optional.of(run));
        var summaryService = new PaperRunSummaryService(service, mock(PaperTradingMonitorService.class),
                mock(PaperRunMonitorService.class), mock(PaperRunRecoveryService.class),
                Clock.systemUTC(), economics);
        assertEquals(0, summaryService.summarize("sim").resultReview().netPnl()
                .compareTo(new BigDecimal("12.50")));
    }
}
