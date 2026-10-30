package com.guidinglight.nexusquant.strategy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.guidinglight.nexusquant.strategy.domain.SpotTargetStrategy.PositionState.*;
import static org.junit.jupiter.api.Assertions.*;

class SpotTargetStrategyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SpotTargetStrategy strategy = channel("20", "10", "0.5");

    @Test
    void closeConfirmationExcludesCurrentBarAndEqualityNeverTriggers() {
        var history = history(20);
        assertEquals("INSUFFICIENT_HISTORY", strategy.evaluate(history, FLAT).reason());
        history.add(bar(20, "100", "999", "90", "101"));
        assertEquals("HOLD_FLAT", strategy.evaluate(history, FLAT).reason());
        history.set(20, bar(20, "100", "999", "90", "102"));
        var enter = strategy.evaluate(history, FLAT);
        assertEquals(SpotTargetStrategy.Action.ENTER_LONG, enter.action());
        assertEquals(new BigDecimal("0.5"), enter.targetExposure());
        assertEquals(history.getLast().availableAt(), enter.availableAt());
        history.set(20, bar(20, "100", "102", "90", "99"));
        assertFalse(strategy.evaluate(history, FLAT).requestsTrade(), "盘中刺穿不能替代收盘确认");
    }

    @Test
    void longStateHoldsThroughPriceChangesAndExitsOnlyStrictlyBelowPriorLows() {
        var history = history(20);
        history.add(bar(20, "100", "101", "90", "99"));
        var hold = strategy.evaluate(history, LONG);
        assertEquals("HOLD_LONG", hold.reason());
        assertEquals(LONG, hold.desiredState());
        assertFalse(hold.requestsTrade());
        history.set(20, bar(20, "100", "101", "90", "98"));
        assertEquals("EXIT_LONG", strategy.evaluate(history, LONG).reason());
        assertEquals(BigDecimal.ZERO, strategy.evaluate(history, LONG).targetExposure());
        assertFalse(strategy.evaluate(history, FLAT).requestsTrade());
    }

    @Test
    void durableDesiredTargetRestoresIntentIndependentlyOfFillOrAccountPosition() {
        var history = history(20);
        history.add(bar(20, "100", "103", "99", "102"));
        var first = strategy.evaluate(history, FLAT);
        var restored = SpotTargetStrategy.fromSnapshot(snapshot("20", "10", "0.5"), mapper);
        history.add(bar(21, "102", "103", "99", "100"));
        var next = restored.evaluate(history, restored.stateForTarget(first.targetExposure()));
        assertEquals("HOLD_LONG", next.reason());
        assertFalse(next.requestsTrade());
        assertTrue(strategy.sameFrozenStrategy(restored));
        assertThrows(IllegalStateException.class, () -> strategy.stateForTarget(new BigDecimal("0.4")));
    }

    @Test
    void rejectsMalformedParametersUnsupportedExecutableAndInvalidBarWindows() {
        assertThrows(IllegalArgumentException.class, () -> channel("20.5", "10", "1"));
        assertThrows(IllegalArgumentException.class, () -> channel("20", "20", "1"));
        assertThrows(IllegalArgumentException.class, () -> channel("201", "10", "1"));
        assertThrows(IllegalArgumentException.class, () -> channel("20", "1", "1"));
        assertThrows(IllegalArgumentException.class, () -> channel("20", "10", "0.000000001"));
        assertThrows(IllegalArgumentException.class, () -> SpotTargetStrategy.fromSnapshot(
                snapshot("20", "10", "1").replace("SPOT_CHANNEL_BREAKOUT_V1", "UNKNOWN"), mapper));
        assertThrows(IllegalArgumentException.class, () -> SpotTargetStrategy.fromSnapshot(
                snapshot("20", "10", "1").replace("\"currentBarExcluded\":true", "\"currentBarExcluded\":false"), mapper));
        assertThrows(IllegalArgumentException.class, () -> strategy.evaluate(List.of(history(2).getFirst(), history(3).getLast()), FLAT));
        var invalid = history(20);
        invalid.add(bar(20, "100", "99", "98", "100"));
        assertThrows(IllegalArgumentException.class, () -> strategy.evaluate(invalid, FLAT));
    }

    @Test
    void sharedSmaContractKeepsItsOriginalTargetAndRebalancingBehavior() {
        String json = snapshot("20", "10", "0.5")
                .replace("SPOT_CHANNEL_BREAKOUT_V1", "SPOT_SMA_TARGET_V1")
                .replace("\"entryLookbackBars\":20", "\"window\":20");
        var shared = SpotTargetStrategy.fromSnapshot(json, mapper);
        var original = SpotSmaTargetStrategy.fromSnapshot(json, mapper);
        var bars = history(24);
        bars.set(23, bar(23, "100", "110", "99", "109"));
        for (int end = 1; end <= bars.size(); end++) {
            var prefix = bars.subList(0, end);
            assertEquals(original.evaluate(prefix).targetExposure(), shared.evaluate(prefix, FLAT).targetExposure());
            assertEquals(original.evaluate(prefix).reason(), shared.evaluate(prefix, LONG).reason());
            assertEquals(SpotTargetStrategy.Action.REBALANCE, shared.evaluate(prefix, FLAT).action());
        }
    }

    @Test
    void boundedResearchWindowMatchesEveryFullPrefixWithoutExpandingDefaultIdentity() {
        var bars=history(4320);
        for(int index=0;index<bars.size();index++) {
            int close=100+index%31;
            bars.set(index,bar(index,Integer.toString(close),Integer.toString(close+2),
                    Integer.toString(close-2),Integer.toString(close)));
        }
        var sma=SpotTargetStrategy.fromSnapshot(snapshot("200","10","0.5")
                .replace("SPOT_CHANNEL_BREAKOUT_V1","SPOT_SMA_TARGET_V1")
                .replace("\"entryLookbackBars\":200","\"window\":200"),mapper);
        for(var candidate:List.of(channel("200","10","0.5"),sma)) {
            var fullState=FLAT;var boundedState=FLAT;
            candidate.validate(bars);
            for(int end=1;end<=bars.size();end++) {
                var full=candidate.evaluate(bars.subList(0,end),fullState);
                var bounded=candidate.evaluate(bars.subList(Math.max(0,end-201),end),boundedState);
                assertEquals(full,bounded,"研究窗口必须保留原逐前缀语义");
                fullState=full.desiredState();boundedState=bounded.desiredState();
            }
        }
        assertEquals(SpotBarIdentity.capture(bars.subList(0,720),mapper),
                SpotBarIdentity.captureResearch(bars.subList(0,720),mapper));
        assertThrows(IllegalArgumentException.class,()->SpotBarIdentity.capture(bars.subList(0,721),mapper));
        assertDoesNotThrow(()->SpotBarIdentity.captureResearch(bars,mapper));
        var oversized=new ArrayList<>(bars);oversized.add(bars.getLast());
        assertThrows(IllegalArgumentException.class,()->SpotBarIdentity.captureResearch(oversized,mapper));
    }

    private SpotTargetStrategy channel(String entry, String exit, String exposure) {
        return SpotTargetStrategy.fromSnapshot(snapshot(entry, exit, exposure), mapper);
    }

    private String snapshot(String entry, String exit, String exposure) {
        return """
                {"strategyVersionId":"sv-channel","checksum":"%s",
                 "sourceSnapshotJson":{"executable":"SPOT_CHANNEL_BREAKOUT_V1","currentBarExcluded":true},
                 "paramSnapshotJson":{"entryLookbackBars":%s,"exitLookbackBars":%s,"investedExposure":"%s"}}
                """.formatted("a".repeat(64), entry, exit, exposure);
    }

    private ArrayList<HistoricalBar> history(int count) {
        var result = new ArrayList<HistoricalBar>();
        for (int index = 0; index < count; index++) result.add(bar(index, "100", "101", "99", "100"));
        return result;
    }

    private HistoricalBar bar(int hour, String open, String high, String low, String close) {
        Instant at = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(hour * 3600L);
        return new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR, at,
                at.plusSeconds(3600).minusMillis(1), new BigDecimal(open), new BigDecimal(high),
                new BigDecimal(low), new BigDecimal(close), BigDecimal.ONE, null, null, "OK", "{}", at.plusSeconds(3600));
    }
}
