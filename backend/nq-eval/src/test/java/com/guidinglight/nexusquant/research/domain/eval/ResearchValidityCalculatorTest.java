package com.guidinglight.nexusquant.research.domain.eval;

import com.guidinglight.nexusquant.research.domain.backtest.ResearchRunFacts;
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchValidityCalculatorTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void serializationPreservesAllEighteenDecimalPlaces() {
        var result = ResearchValidityCalculator.calculate(facts(10), snapshots(10), List.of(), START);
        var precise = new ResearchValidity(result.validationStatus(), result.reason(), result.splitPolicy(),
                result.segmentEquityPolicy(), result.identity(), result.assumptions(), result.full(),
                result.inSample(), result.outOfSample(), result.benchmark(), new BigDecimal("0.123456789012345678"));
        assertEquals(precise, ResearchValidityCalculator.read(ResearchValidityCalculator.append("{}", precise)));
    }

    @Test
    void floorSplitCoversOddEvenAndMinimumCountsWithoutOverlap() {
        for (int count : new int[]{2, 3, 4, 5, 9, 10, 11, 500}) {
            var result = ResearchValidityCalculator.calculate(facts(count), snapshots(count), List.of(), START);
            int split = count * 70 / 100;
            assertEquals(split, result.inSample().barCount());
            assertEquals(count - split, result.outOfSample().barCount());
            assertTrue(Instant.parse(result.inSample().endTime()).isBefore(Instant.parse(result.outOfSample().startTime())));
            assertEquals(result.inSample().finalEquity(), result.outOfSample().startingEquity());
            assertEquals(result.full().netPnl(), result.inSample().netPnl().add(result.outOfSample().netPnl()));
            assertEquals(result.full().fee(), result.inSample().fee().add(result.outOfSample().fee()));
            assertEquals(result.full().slippage(), result.inSample().slippage().add(result.outOfSample().slippage()));
            assertEquals(result, ResearchValidityCalculator.calculate(facts(count), snapshots(count), List.of(), START));
        }
    }

    @Test
    void oneBarAndLegacyNeverInventOosOrZeroReturn() {
        var one = ResearchValidityCalculator.calculate(facts(1), snapshots(1), List.of(), START);
        assertEquals("INSUFFICIENT_DATA", one.validationStatus());
        assertNull(one.inSample());
        assertNull(one.outOfSample());
        assertNotNull(one.full());
        var old = ResearchValidityCalculator.read("{\"finalEquity\":\"100\"}");
        assertEquals("NOT_AVAILABLE", old.validationStatus());
        assertNull(old.benchmark());
        assertNull(old.strategyVsBenchmarkDifference());
    }

    @Test
    void futureSnapshotsDoNotAlterInSampleAndEarlierOutOfSamplePrefix() {
        var input = snapshots(10);
        var original = ResearchValidityCalculator.calculate(facts(10), input, List.of(), START);
        var changed = new ArrayList<>(input);
        var last = input.getLast();
        changed.set(9, new SimPnlSnapshot(last.simPnlSnapshotId(), "run", last.snapshotTime(),
                last.cashBalance(), last.positionMarketValue(), last.realizedPnl(), last.unrealizedPnl(),
                last.totalFee(), last.totalSlippage(), new BigDecimal("1"), new BigDecimal("-99"), last.createdAt()));
        var future = ResearchValidityCalculator.calculate(facts(10), changed, List.of(), START);
        assertEquals(original.inSample(), future.inSample());
        assertNotEquals(original.outOfSample().strategyReturn(), future.outOfSample().strategyReturn());
        assertEquals(input.subList(7, 9), changed.subList(7, 9));
    }

    @Test
    void rejectsMissingDuplicatedAndWrongRunFacts() {
        var input = snapshots(10);
        assertThrows(IllegalStateException.class, () -> ResearchValidityCalculator.calculate(facts(10), input.subList(0, 9), List.of(), START));
        var duplicate = new ArrayList<>(input);
        duplicate.set(8, input.get(7));
        assertThrows(IllegalStateException.class, () -> ResearchValidityCalculator.calculate(facts(10), duplicate, List.of(), START));
    }

    @Test
    void zeroSegmentBaselineLeavesReturnUnavailableAndDrawdownIncludesBoundaryCapital() {
        var input = new ArrayList<>(snapshots(2));
        var first = input.getFirst();
        input.set(0, new SimPnlSnapshot("p0", "run", first.snapshotTime(), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("-100"), first.createdAt()));
        var result = ResearchValidityCalculator.calculate(facts(2), input, List.of(), START);
        assertNull(result.outOfSample().strategyReturn());
        assertEquals("NOT_AVAILABLE", result.outOfSample().status());
        assertEquals(0, BigDecimal.ONE.compareTo(result.inSample().maxDrawdownRate()));
    }

    private ResearchRunFacts facts(int count) {
        var bars = new ArrayList<ResearchRunFacts.BarWindow>();
        for (int i = 0; i < count; i++) {
            var open = START.plusSeconds(i * 60L);
            bars.add(new ResearchRunFacts.BarWindow(open.toString(), open.plusSeconds(59).toString(), open.plusSeconds(59).toString()));
        }
        return new ResearchRunFacts("run", "dataset", "digest", "version", "checksum", "BTC-USDT", "1m", "{}",
                new ResearchRunFacts.Assumptions(new BigDecimal("100"), new BigDecimal("0.001"),
                        new BigDecimal("10"), "NEXT_OPEN_STRICTLY_AFTER_AVAILABLE_AT", "MARK_TO_MARKET", "{}", new BigDecimal("100")), bars, null);
    }

    private List<SimPnlSnapshot> snapshots(int count) {
        var result = new ArrayList<SimPnlSnapshot>();
        for (int i = 0; i < count; i++) {
            var time = START.plusSeconds(i * 60L + 59);
            var equity = BigDecimal.valueOf(100 + i);
            result.add(new SimPnlSnapshot("p" + i, "run", time, equity, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.valueOf(i), BigDecimal.valueOf(i * 2L), equity,
                    equity.subtract(new BigDecimal("100")), time));
        }
        return result;
    }
}
