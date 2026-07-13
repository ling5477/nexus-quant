package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFact;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFactsSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxVenueRuleFactsProvider;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.OkxVenueRuleContract;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class OkxVenueRuleFactsSyncServiceTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-07-13T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(OBSERVED_AT.plusSeconds(1), ZoneOffset.UTC);

    @Test
    void shouldPersistCompleteSnapshotWithSourceVersionObservationAndChecksum() {
        RecordingRepository repository = new RecordingRepository();
        OkxVenueRuleFactsSyncService service = service(repository, (symbols, traceId) -> snapshot("LIVE"));

        OkxVenueRuleFactsSyncResult result = service.sync(Set.of("BTC-USDT"), "trace-sync");

        InstrumentCatalogItem item = repository.upsertedItems.getFirst();
        assertEquals(OkxVenueRuleContract.SOURCE, item.source());
        assertEquals(OkxVenueRuleContract.SOURCE_SCHEMA_VERSION, item.sourceSchemaVersion());
        assertEquals(OBSERVED_AT, item.observedAt());
        assertEquals("USDT", item.maxMarketSizeUnit());
        assertNotNull(item.ruleChecksum());
        assertEquals(64, item.ruleChecksum().length());
        assertEquals(1, result.insertedCount());
        assertEquals(OBSERVED_AT.plusSeconds(1), result.syncedAt());
    }

    @Test
    void nonLiveSnapshotShouldStillReachRepository() {
        RecordingRepository repository = new RecordingRepository();

        service(repository, (symbols, traceId) -> snapshot("SUSPEND"))
                .sync(Set.of("BTC-USDT"), "trace-suspend");

        assertEquals("SUSPEND", repository.upsertedItems.getFirst().status());
    }

    @Test
    void readerFailureShouldPreserveOldFactsWithoutAnyRepositoryCall() {
        RecordingRepository repository = new RecordingRepository();
        OkxVenueRuleFactsProvider failingProvider = (symbols, traceId) -> {
            throw new IllegalStateException("public response invalid");
        };

        assertThrows(
                IllegalStateException.class,
                () -> service(repository, failingProvider).sync(Set.of("BTC-USDT"), "trace-failure")
        );

        assertEquals(0, repository.readCount);
        assertEquals(0, repository.upsertCount);
    }

    @Test
    void shouldRejectUnallowlistedOrMoreThanThreeSymbolsBeforeNetwork() {
        RecordingRepository repository = new RecordingRepository();
        CountingProvider provider = new CountingProvider();
        OkxVenueRuleFactsSyncService service = service(repository, provider);

        assertThrows(IllegalArgumentException.class, () -> service.sync(Set.of("DOGE-USDT"), "trace-denied"));
        assertThrows(IllegalArgumentException.class, () -> service.sync(
                Set.of("BTC-USDT", "ETH-USDT", "SOL-USDT", "DOGE-USDT"),
                "trace-too-many"
        ));
        assertEquals(0, provider.fetchCount);
    }

    @Test
    void constructorShouldRejectNonOkxSpotStyleAllowlist() {
        assertThrows(IllegalArgumentException.class, () -> new OkxVenueRuleFactsSyncService(
                new InstrumentCatalogService(new RecordingRepository()),
                (symbols, traceId) -> snapshot("LIVE"),
                CLOCK,
                Set.of("BTCUSDT")
        ));
    }

    private static OkxVenueRuleFactsSyncService service(
            RecordingRepository repository,
            OkxVenueRuleFactsProvider provider
    ) {
        return new OkxVenueRuleFactsSyncService(
                new InstrumentCatalogService(repository),
                provider,
                CLOCK,
                Set.of("BTC-USDT", "ETH-USDT", "SOL-USDT")
        );
    }

    private static OkxVenueRuleFactsSnapshot snapshot(String state) {
        return new OkxVenueRuleFactsSnapshot(List.of(new OkxVenueRuleFact(
                "BTC-USDT",
                "SPOT",
                state,
                "BTC",
                "USDT",
                new BigDecimal("0.1"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.001"),
                new BigDecimal("100"),
                new BigDecimal("100000"),
                "USDT",
                new BigDecimal("1000000"),
                new BigDecimal("1000000"),
                null
        )), OBSERVED_AT);
    }

    private static class RecordingRepository implements InstrumentCatalogRepository {

        private int readCount;
        private int upsertCount;
        private List<InstrumentCatalogItem> existingItems = List.of();
        private List<InstrumentCatalogItem> upsertedItems = new ArrayList<>();

        @Override
        public List<InstrumentCatalogItem> list(String exchangeCode) {
            return List.of();
        }

        @Override
        public List<InstrumentCatalogItem> findByExchangeAndSymbols(
                String exchangeCode,
                List<String> exchangeSymbols
        ) {
            readCount++;
            return existingItems;
        }

        @Override
        public InstrumentCatalogUpsertStats upsertAll(List<InstrumentCatalogItem> items, Instant syncedAt) {
            throw new UnsupportedOperationException("legacy upsert is outside this test");
        }

        @Override
        public InstrumentCatalogUpsertStats upsertVenueRuleFacts(
                List<InstrumentCatalogItem> items,
                Instant syncedAt
        ) {
            upsertCount++;
            upsertedItems = List.copyOf(items);
            return new InstrumentCatalogUpsertStats(items.size(), 0);
        }
    }

    private static final class CountingProvider implements OkxVenueRuleFactsProvider {

        private int fetchCount;

        @Override
        public OkxVenueRuleFactsSnapshot fetch(Set<String> allowlistedSymbols, String traceId) {
            fetchCount++;
            return snapshot("LIVE");
        }
    }
}
