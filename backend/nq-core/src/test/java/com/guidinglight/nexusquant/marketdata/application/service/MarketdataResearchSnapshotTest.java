package com.guidinglight.nexusquant.marketdata.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.*;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketdataResearchSnapshotTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-10-26T00:00:00Z");

    @Test void retainsLegacyDescriptorOutsideCompleteResearchWindow() throws Exception {
        MarketdataDataset dataset = dataset(Instant.parse("2026-09-01T00:30:00Z"));
        var repository = repository(dataset);
        var service = new MarketdataDatasetService(repository, mapper, Clock.fixed(now, ZoneOffset.UTC), query -> {
            throw new AssertionError("legacy descriptor must not trigger the new research reader");
        });
        var snapshot = mapper.readTree(service.buildDatasetSnapshot(dataset.datasetId()));
        assertEquals("db", snapshot.path("provider").asText());
        assertFalse(snapshot.has("frozenInput"));
        assertEquals(dataset.startTime().toString(), snapshot.path("startTime").asText());
    }

    @Test void completeResearchDescriptorCannotFallBackWhenActualSourceHasGaps() {
        MarketdataDataset dataset = dataset(Instant.parse("2026-09-01T00:00:00Z"));
        var repository = repository(dataset);
        var service = new MarketdataDatasetService(repository, mapper, Clock.fixed(now, ZoneOffset.UTC), query -> List.of());
        assertThrows(IllegalStateException.class, () -> service.buildDatasetSnapshot(dataset.datasetId()));
    }

    private MarketdataDataset dataset(Instant start) {
        return new MarketdataDataset(UUID.randomUUID(), "research", "OKX", "SPOT", "ETH-USDT", BarInterval.ONE_HOUR,
                start, start.plusSeconds(7200).minusMillis(1), MarketdataDatasetStatus.READY, MarketdataQualityStatus.OK,
                2, 0, "marketdata_bars", "test", now, now, "{}");
    }

    private MarketdataDatasetRepository repository(MarketdataDataset dataset) {
        return (MarketdataDatasetRepository) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {MarketdataDatasetRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("findByDatasetId")) return Optional.of(dataset);
                    throw new AssertionError("unexpected repository operation: " + method.getName());
                });
    }
}
