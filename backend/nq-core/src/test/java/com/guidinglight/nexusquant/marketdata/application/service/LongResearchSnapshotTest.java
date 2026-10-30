package com.guidinglight.nexusquant.marketdata.application.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.*;
import com.guidinglight.nexusquant.marketdata.domain.port.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LongResearchSnapshotTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-12-01T00:00:00Z");

    @Test void longSnapshotReadsBoundedPagesAndPinsOriginalReference() throws Exception {
        var dataset = dataset(4320);
        var source = bars(dataset);
        var pages = new ArrayList<Integer>();
        var service = new MarketdataDatasetService(repository(dataset), mapper, Clock.fixed(now, ZoneOffset.UTC), query -> {
            assertEquals(500, query.size()); pages.add(query.page());
            int start = query.page() * query.size();
            return List.copyOf(source.subList(start, Math.min(source.size(), start + query.size())));
        });
        var port = new InMemoryVersions(); service.bindResearchDatasetVersions(port);
        String first = service.buildDatasetSnapshot(dataset.datasetId());
        assertEquals(9, pages.size());
        assertEquals(List.of(0,1,2,3,4,5,6,7,8), pages);
        assertEquals(4320, port.rows.size()); assertEquals(1, port.freezes);
        var snapshot = mapper.readTree(first);
        assertEquals(4320, snapshot.path("frozenInput").path("barCount").asInt());
        assertFalse(snapshot.path("frozenInput").has("bars"));
        source.clear();
        assertEquals(first, service.buildDatasetSnapshot(dataset.datasetId()));
        assertEquals(9, pages.size()); assertEquals(1, port.freezes);
        assertEquals(4320, service.readFrozenInput(snapshot).size());
    }

    @Test void oversizedAndMissingStorageCannotFallBackToMutableReader() {
        var oversized = dataset(4321);
        var service = new MarketdataDatasetService(repository(oversized), mapper, Clock.fixed(now, ZoneOffset.UTC), q -> {
            throw new AssertionError("over-limit descriptor cannot read rows");
        });
        assertThrows(IllegalStateException.class, () -> service.buildDatasetSnapshot(oversized.datasetId()));
        var longDataset = dataset(721);
        var missing = new MarketdataDatasetService(repository(longDataset), mapper, Clock.fixed(now, ZoneOffset.UTC), q -> List.of());
        assertThrows(IllegalStateException.class, () -> missing.buildDatasetSnapshot(longDataset.datasetId()));
    }

    private MarketdataDataset dataset(int count) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        return new MarketdataDataset(UUID.randomUUID(), "long", "OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR,
                start, start.plusSeconds(3600L * count).minusMillis(1), MarketdataDatasetStatus.READY, MarketdataQualityStatus.OK,
                count, 0, "marketdata_bars", "test", now, now, "{}");
    }

    private ArrayList<HistoricalBar> bars(MarketdataDataset dataset) {
        var result = new ArrayList<HistoricalBar>();
        for (int index = 0; index < dataset.barCount(); index++) {
            Instant open = dataset.startTime().plusSeconds(3600L * index);
            result.add(new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR, open,
                    open.plusSeconds(3600).minusMillis(1), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, BigDecimal.ZERO, null, null, "OK", "{}", now));
        }
        return result;
    }

    private MarketdataDatasetRepository repository(MarketdataDataset dataset) {
        return (MarketdataDatasetRepository) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MarketdataDatasetRepository.class}, (proxy, method, args) -> {
                    if ("findByDatasetId".equals(method.getName())) return Optional.of(dataset);
                    throw new AssertionError(method.getName());
                });
    }

    private final class InMemoryVersions implements ResearchDatasetVersionPort {
        private JsonNode reference;
        private List<HistoricalBar> rows;
        private int freezes;
        @Override public Optional<JsonNode> find(UUID id) { return Optional.ofNullable(reference); }
        @Override public JsonNode freeze(JsonNode dataset, List<HistoricalBar> source, Instant frozenAt) {
            freezes++; rows = List.copyOf(source);
            var full = ResearchHistoricalInput.freeze(dataset, source, frozenAt, mapper);
            full.remove(List.of("bars", "sourceObservedAt"));
            full.put("datasetVersionId", UUID.randomUUID().toString()); full.put("manifestSha256", "a".repeat(64));
            full.put("barCount", source.size()); reference = full; return reference;
        }
        @Override public List<HistoricalBar> read(JsonNode dataset) { return rows; }
    }
}
