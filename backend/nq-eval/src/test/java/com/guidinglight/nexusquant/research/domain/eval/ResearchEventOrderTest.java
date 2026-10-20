package com.guidinglight.nexusquant.research.domain.eval;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.research.domain.backtest.ResearchRunFacts;
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 独立审查原反例的因果次序：同开盘BUY→SELL→BUY不能由随机ID改写，同可见时间也不能伪造微秒。 */
class ResearchEventOrderTest {
    private static final Instant START = Instant.parse("2026-09-25T00:00:00Z");

    @Test
    void sameTimeTradesConsumeRealSequenceDespiteReversedRandomIds() {
        var at = START.plusSeconds(240);
        var trades =
                List.of(
                        trade("a-sell", "SELL", at, 2L),
                        trade("b-buy", "BUY", at, 3L),
                        trade("z-buy", "BUY", at, 1L),
                        trade("c-sell", "SELL", at.plusSeconds(60), 4L));
        assertEquals(
                List.of("z-buy", "a-sell", "b-buy", "c-sell"),
                TradeOutcomeCalculator.orderedTrades(trades).stream()
                        .map(SimTrade::simTradeId)
                        .toList());
        var result = new TradeOutcomeCalculator().calculate(trades, true);
        assertEquals(2, result.flatTradeCount());
        assertEquals(0, result.winningTradeCount());
        assertEquals(0, result.losingTradeCount());
    }

    @Test
    void legacySameTimeEvidenceIsUnavailableAndUniqueTimeRemainsCompatible() {
        var at = START.plusSeconds(240);
        assertEquals(
                "RESEARCH_TRADE_ORDER_UNAVAILABLE",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new TradeOutcomeCalculator()
                                                .calculate(
                                                        List.of(
                                                                trade("buy", "BUY", at, null),
                                                                trade("sell", "SELL", at, null))))
                        .getMessage());
        var result =
                new TradeOutcomeCalculator()
                        .calculate(
                                List.of(
                                        trade("sell", "SELL", at.plusSeconds(1), null),
                                        trade("buy", "BUY", at, null)));
        assertEquals(1, result.flatTradeCount());
    }

    @Test
    void mixedDuplicateGapAndChronologicallyContradictoryExecutionSequencesAreRejected() {
        var at = START.plusSeconds(240);
        for (List<SimTrade> input :
                List.of(
                        List.of(trade("a", "BUY", at, 1L), trade("b", "SELL", at, null)),
                        List.of(trade("a", "BUY", at, 1L), trade("b", "SELL", at, 1L)),
                        List.of(trade("a", "BUY", at, 1L), trade("b", "SELL", at, 3L)),
                        List.of(
                                trade("a", "BUY", at.plusSeconds(1), 1L),
                                trade("b", "SELL", at, 2L))))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TradeOutcomeCalculator().calculate(input));
        assertThrows(IllegalArgumentException.class, () -> trade("bad", "BUY", at, 0L));
    }

    @Test
    void sameAvailabilitySnapshotsRemainOriginalTimesAndFollowRealBarOrder() {
        var facts = facts(true);
        var original = snapshots(facts, true);
        var reversed = new ArrayList<>(original);
        Collections.reverse(reversed);
        var result = ResearchValidityCalculator.calculate(facts, reversed, List.of(), START);
        assertEquals("AVAILABLE", result.validationStatus());
        assertEquals(8, result.full().barCount());
        assertEquals(0, new BigDecimal("107").compareTo(result.full().finalEquity()));
        assertEquals(START.plusSeconds(239), original.get(1).snapshotTime());
        assertEquals(original.get(1).snapshotTime(), original.get(2).snapshotTime());
        assertEquals(original.get(2).snapshotTime(), original.get(3).snapshotTime());
    }

    @Test
    void ambiguousLegacyValuationIsRejectedWhileOldUniqueTimeFactsStayUsable() {
        var same = facts(true);
        assertEquals(
                "RESEARCH_VALUATION_ORDER_UNAVAILABLE",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        ResearchValidityCalculator.calculate(
                                                same, snapshots(same, false), List.of(), START))
                        .getMessage());
        assertThrows(
                IllegalStateException.class,
                () ->
                        ResearchValidityCalculator.calculate(
                                null, snapshots(same, false), List.of(), START));
        var unique = facts(false);
        assertEquals(
                "AVAILABLE",
                ResearchValidityCalculator.calculate(
                                unique, snapshots(unique, false), List.of(), START)
                        .validationStatus());
    }

    @Test
    void valuationIdentityGapsAndOriginalBarTimeGuardsArePreserved() {
        var facts = facts(true);
        var original = snapshots(facts, true);
        var gap = new ArrayList<>(original);
        gap.set(2, original.get(2).withValuationSequence(9));
        assertThrows(
                IllegalStateException.class,
                () -> ResearchValidityCalculator.calculate(facts, gap, List.of(), START));
        var mixed = new ArrayList<>(original);
        mixed.set(2, snapshots(facts, false).get(2));
        assertThrows(
                IllegalStateException.class,
                () -> ResearchValidityCalculator.calculate(facts, mixed, List.of(), START));
        var wrong = new ArrayList<>(original);
        var row = original.getFirst();
        wrong.set(
                0,
                new SimPnlSnapshot(
                                row.simPnlSnapshotId(),
                                "run",
                                START.plusSeconds(58),
                                row.cashBalance(),
                                row.positionMarketValue(),
                                row.realizedPnl(),
                                row.unrealizedPnl(),
                                row.totalFee(),
                                row.totalSlippage(),
                                row.equity(),
                                row.netPnl(),
                                row.createdAt())
                        .withValuationSequence(1));
        assertThrows(
                IllegalStateException.class,
                () -> ResearchValidityCalculator.calculate(facts, wrong, List.of(), START));
    }

    private static SimTrade trade(String id, String side, Instant at, Long sequence) {
        var trade =
                new SimTrade(
                        id,
                        "order",
                        "run",
                        "BTC-USDT",
                        side,
                        BigDecimal.ONE,
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        at,
                        at,
                        at);
        return sequence == null ? trade : trade.withExecutionSequence(sequence);
    }

    private static ResearchRunFacts facts(boolean delayed) {
        var bars = new ArrayList<ResearchRunFacts.BarWindow>();
        for (int index = 0; index < 8; index++) {
            Instant opening = START.plusSeconds(index * 60L), closing = opening.plusSeconds(59);
            Instant visible =
                    delayed && index >= 1 && index <= 3 ? START.plusSeconds(239) : closing;
            bars.add(
                    new ResearchRunFacts.BarWindow(
                            opening.toString(), closing.toString(), visible.toString()));
        }
        return new ResearchRunFacts(
                "run",
                "dataset",
                "digest",
                "version",
                "checksum",
                "BTC-USDT",
                "1m",
                "{}",
                new ResearchRunFacts.Assumptions(
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "NEXT_OPEN_STRICTLY_AFTER_AVAILABLE_AT",
                        "MARK_TO_MARKET",
                        "{}",
                        new BigDecimal("100")),
                bars,
                null);
    }

    private static List<SimPnlSnapshot> snapshots(ResearchRunFacts facts, boolean sequenced) {
        var rows = new ArrayList<SimPnlSnapshot>();
        for (int index = 0; index < facts.bars().size(); index++) {
            Instant visible = Instant.parse(facts.bars().get(index).snapshotTime());
            BigDecimal equity = BigDecimal.valueOf(100L + index);
            var row =
                    new SimPnlSnapshot(
                            "p" + (8 - index),
                            "run",
                            visible,
                            equity,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            equity,
                            equity.subtract(new BigDecimal("100")),
                            visible);
            rows.add(sequenced ? row.withValuationSequence(index + 1L) : row);
        }
        return rows;
    }
}
