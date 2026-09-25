package com.guidinglight.nexusquant.strategy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpotSmaTargetStrategyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SpotSmaTargetStrategy strategy = SpotSmaTargetStrategy.fromSnapshot("""
            {"strategyVersionId":"sv-1","checksum":"sha-1",
             "sourceSnapshotJson":{"executable":"SPOT_SMA_TARGET_V1"},
             "paramSnapshotJson":{"window":3,"investedExposure":"1"}}
            """, mapper);

    @Test
    void usesOnlyClosedVisiblePrefixAndStableInputIdentity() {
        List<HistoricalBar> bars = List.of(bar(0, "100"), bar(1, "101"), bar(2, "102"), bar(3, "99"));
        assertEquals("INSUFFICIENT_HISTORY", strategy.evaluate(bars.subList(0, 2)).reason());
        assertEquals(new BigDecimal("1"), strategy.evaluate(bars.subList(0, 3)).targetExposure());
        assertEquals(Instant.parse("2026-01-01T00:02:59Z"),
                strategy.evaluate(bars.subList(0, 3)).availableAt());
        assertEquals(BigDecimal.ZERO, strategy.evaluate(bars).targetExposure());
        String digest = SpotBarIdentity.capture(bars, mapper).sha256();
        assertEquals(digest, SpotBarIdentity.capture(List.copyOf(bars), mapper).sha256());
        assertTrue(!digest.equals(SpotBarIdentity.capture(List.of(bar(0, "100"), bar(1, "101"),
                bar(2, "103"), bar(3, "99")), mapper).sha256()));
    }

    @Test
    void rejectsMissingDuplicateOutOfOrderAndLateVisibility() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(List.of(bar(0, "100"), bar(2, "102"))));
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(List.of(bar(0, "100"), bar(0, "100"))));
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(List.of(bar(1, "101"), bar(0, "100"))));
        HistoricalBar late = new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_MINUTE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:59Z"),
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("100"), BigDecimal.ONE, null, null, "OK", "{}",
                Instant.parse("2026-01-01T00:02:00Z"));
        assertEquals(late.availableAt(), strategy.evaluate(List.of(late)).availableAt());
        assertEquals("LATE_ARRIVING_BAR_IN_SIGNAL_WINDOW", assertThrows(
                IllegalArgumentException.class,
                () -> strategy.evaluate(List.of(late, bar(1, "101")))).getMessage());
    }

    private HistoricalBar bar(int minute, String close) {
        Instant open = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(minute * 60L);
        BigDecimal price = new BigDecimal(close);
        return new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_MINUTE,
                open, open.plusSeconds(59), price, price, price, price, BigDecimal.ONE,
                null, null, "OK", "{}", open.plusSeconds(59));
    }
}
