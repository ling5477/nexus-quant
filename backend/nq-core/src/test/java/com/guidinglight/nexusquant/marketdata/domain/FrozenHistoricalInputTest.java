package com.guidinglight.nexusquant.marketdata.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrozenHistoricalInputTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant observed = Instant.parse("2026-03-01T00:00:00Z");

    @Test void freezesFull720SequenceAndSeparatesObservationFromReplayVisibility() {
        for (String symbol : List.of("BTC-USDT", "ETH-USDT")) {
            ObjectNode dataset = dataset(symbol, 720);
            var source = bars(symbol, 720);
            dataset.set("frozenInput", FrozenHistoricalInput.freeze(dataset, source, observed, mapper));
            source.clear();
            var frozen = FrozenHistoricalInput.read(dataset, mapper);
            assertEquals(720, frozen.size());
            assertEquals(symbol, frozen.getLast().symbol());
            assertEquals(Instant.parse("2026-01-01T01:00:00Z"), frozen.getFirst().availableAt());
            assertEquals(observed.toString(), dataset.path("frozenInput").path("sourceObservedAt").get(0).asText());
            ((ObjectNode) dataset.path("frozenInput").path("bars").get(719)).put("volume", "2");
            assertThrows(IllegalStateException.class, () -> FrozenHistoricalInput.read(dataset, mapper));
        }
    }

    @Test void rejectsMissingDuplicateOutOfOrderMixedAndUnclosedInputs() {
        for (int type = 0; type < 5; type++) {
            ObjectNode dataset = dataset("BTC-USDT", 3);
            var source = bars("BTC-USDT", 3);
            switch (type) {
                case 0 -> source.remove(1);
                case 1 -> source.set(1, source.getFirst());
                case 2 -> java.util.Collections.swap(source, 0, 1);
                case 3 -> source.set(1, bars("ETH-USDT", 3).get(1));
                case 4 -> dataset.put("endTime", "2026-04-01T00:00:00Z");
                default -> throw new AssertionError();
            }
            assertThrows(IllegalStateException.class, () -> FrozenHistoricalInput.freeze(dataset, source, observed, mapper));
        }
    }

    @Test void rejectsActual721BarsAndInvalidOhlcWithoutSilentlyTruncating() {
        assertThrows(IllegalStateException.class,
                () -> FrozenHistoricalInput.freeze(dataset("BTC-USDT", 720), bars("BTC-USDT", 721), observed, mapper));
        var source = bars("BTC-USDT", 1);
        var bar = source.getFirst();
        source.set(0, new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR,
                bar.openTime(), bar.closeTime(), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ONE, null, null, "OK", "{}", observed));
        assertThrows(IllegalStateException.class,
                () -> FrozenHistoricalInput.freeze(dataset("BTC-USDT", 1), source, observed, mapper));
    }

    @Test void rejectsEarlierUnclosedObservationEvenAfterHistoricalWindowHasEnded() {
        var source = bars("ETH-USDT", 1);
        var bar = source.getFirst();
        for (boolean early : List.of(true, false)) {
            source.set(0, new HistoricalBar(bar.exchangeCode(), bar.marketType(), bar.symbol(), bar.interval(),
                    bar.openTime(), bar.closeTime(), bar.openPrice(), bar.highPrice(), bar.lowPrice(),
                    bar.closePrice(), bar.volume(), bar.quoteVolume(), bar.tradeCount(), bar.qualityStatus(),
                    early ? "{}" : "[1767225600000,1,1,1,1,0,0,0,0]",
                    early ? bar.openTime().plusSeconds(10) : observed));
            assertThrows(IllegalStateException.class, () -> FrozenHistoricalInput.freeze(
                    dataset("ETH-USDT", 1), source, observed, mapper));
        }
    }

    private ObjectNode dataset(String symbol, int count) {
        ObjectNode value = mapper.createObjectNode();
        value.put("provider", "db"); value.put("exchangeCode", "OKX"); value.put("marketType", "SPOT");
        value.put("symbol", symbol); value.put("interval", "1h"); value.put("status", "READY");
        value.put("qualityStatus", "OK"); value.put("source", "marketdata_bars");
        value.put("barCount", count); value.put("gapCount", 0);
        value.put("startTime", "2026-01-01T00:00:00Z");
        value.put("endTime", Instant.parse("2026-01-01T00:00:00Z").plusSeconds(3600L * count).minusMillis(1).toString());
        return value;
    }

    private ArrayList<HistoricalBar> bars(String symbol, int count) {
        var bars = new ArrayList<HistoricalBar>();
        for (int index = 0; index < count; index++) {
            Instant open = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(3600L * index);
            bars.add(new HistoricalBar("OKX", "SPOT", symbol, BarInterval.ONE_HOUR, open,
                    open.plusSeconds(3600).minusMillis(1), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, "OK", "{}", observed));
        }
        return bars;
    }
}
