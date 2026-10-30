package com.guidinglight.nexusquant.marketdata.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchHistoricalInputTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant start = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant frozenAt = Instant.parse("2026-12-01T00:00:00Z");

    @Test void researchCanRead7212160And4320WithoutChangingLegacySimIdentity() {
        for (int count : List.of(721, 2160, 4320)) {
            ObjectNode dataset = dataset(count);
            var source = bars(count);
            dataset.set("frozenInput", ResearchHistoricalInput.freeze(dataset, source, frozenAt, mapper));
            var replay = ResearchHistoricalInput.readMaterialized(dataset, mapper);
            assertEquals(count, replay.size());
            assertEquals(start.plusSeconds(3600L * count), replay.getLast().availableAt());
            assertEquals(frozenAt.toString(), dataset.path("frozenInput").path("sourceObservedAt").get(0).asText());
            assertEquals(SpotBarIdentity.captureResearch(replay, mapper).sha256(), dataset.path("frozenInput").path("sha256").asText());
            assertThrows(IllegalStateException.class, () -> FrozenHistoricalInput.read(dataset, mapper));
            assertThrows(IllegalArgumentException.class, () -> SpotBarIdentity.capture(replay, mapper));
        }
        assertThrows(IllegalStateException.class, () -> ResearchHistoricalInput.freeze(dataset(720), bars(720), frozenAt, mapper));
        assertThrows(IllegalStateException.class, () -> ResearchHistoricalInput.freeze(dataset(4321), bars(4321), frozenAt, mapper));
    }

    @Test void rejectsMalformedLongSourceAndFutureObservations() {
        for (int kind = 0; kind < 8; kind++) {
            var source = bars(721);
            var bar = source.get(400);
            switch (kind) {
                case 0 -> source.remove(400);
                case 1 -> source.set(400, source.get(399));
                case 2 -> Collections.swap(source, 400, 401);
                case 3 -> source.set(400, copy(bar, "ETH-USDT", bar.highPrice(), "{}", frozenAt));
                case 4 -> source.set(400, copy(bar, bar.symbol(), BigDecimal.ZERO, "{}", frozenAt));
                case 5 -> source.set(400, copy(bar, bar.symbol(), bar.highPrice(), "[1,1,1,1,1,0,0,0,0]", frozenAt));
                case 6 -> source.set(400, copy(bar, bar.symbol(), bar.highPrice(), "{}", frozenAt.plusSeconds(1)));
                case 7 -> source.set(400, copy(bar, bar.symbol(), bar.highPrice(), "{}", bar.openTime().plusSeconds(10)));
                default -> throw new AssertionError();
            }
            assertThrows(IllegalStateException.class, () -> ResearchHistoricalInput.freeze(dataset(721), source, frozenAt, mapper));
        }
    }

    @Test void rechecksPersistedSourceAndConsumedChecksums() {
        for (String field : List.of("sha256", "sourceSha256")) {
            var dataset = dataset(721);
            ObjectNode frozen = ResearchHistoricalInput.freeze(dataset, bars(721), frozenAt, mapper);
            frozen.put(field, "0".repeat(64)); dataset.set("frozenInput", frozen);
            assertThrows(IllegalStateException.class, () -> ResearchHistoricalInput.readMaterialized(dataset, mapper));
        }
        var dataset = dataset(721);
        var frozen = ResearchHistoricalInput.freeze(dataset, bars(721), frozenAt, mapper);
        frozen.withArray("sourceObservedAt").set(400, mapper.getNodeFactory().textNode(frozenAt.plusSeconds(1).toString()));
        dataset.set("frozenInput", frozen);
        assertThrows(IllegalStateException.class, () -> ResearchHistoricalInput.readMaterialized(dataset, mapper));
    }

    private ObjectNode dataset(int count) {
        var dataset = mapper.createObjectNode();
        dataset.put("provider", "db"); dataset.put("exchangeCode", "OKX"); dataset.put("marketType", "SPOT");
        dataset.put("symbol", "BTC-USDT"); dataset.put("interval", "1h"); dataset.put("status", "READY");
        dataset.put("qualityStatus", "OK"); dataset.put("source", "marketdata_bars");
        dataset.put("barCount", count); dataset.put("gapCount", 0); dataset.put("startTime", start.toString());
        dataset.put("endTime", start.plusSeconds(3600L * count).minusMillis(1).toString());
        return dataset;
    }

    private ArrayList<HistoricalBar> bars(int count) {
        var result = new ArrayList<HistoricalBar>();
        for (int index = 0; index < count; index++) {
            Instant open = start.plusSeconds(3600L * index);
            result.add(new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR, open,
                    open.plusSeconds(3600).minusMillis(1), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, BigDecimal.ZERO, null, null, "OK", "{}", frozenAt));
        }
        return result;
    }

    private HistoricalBar copy(HistoricalBar bar, String symbol, BigDecimal high, String raw, Instant observed) {
        return new HistoricalBar(bar.exchangeCode(), bar.marketType(), symbol, bar.interval(), bar.openTime(),
                bar.closeTime(), bar.openPrice(), high, bar.lowPrice(), bar.closePrice(), bar.volume(),
                bar.quoteVolume(), bar.tradeCount(), bar.qualityStatus(), raw, observed);
    }
}
