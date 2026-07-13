package com.guidinglight.nexusquant.marketdata.domain.instrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class VenueRuleFreshnessEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void completeLiveSnapshotShouldBeFresh() {
        InstrumentCatalogItem item = checksummed(item("LIVE", NOW.minusSeconds(30), null, true));

        VenueRuleFreshness result = new VenueRuleFreshnessEvaluator(CLOCK, 600L).evaluate(item);

        assertEquals(VenueRuleFreshness.Availability.AVAILABLE, result.availability());
        assertEquals(VenueRuleFreshness.FreshnessStatus.FRESH, result.freshnessStatus());
        assertEquals(NOW.plusSeconds(570), result.freshUntil());
        assertNull(result.blockingReason());
    }

    @Test
    void expiredSnapshotShouldBeStaleAndBlocked() {
        InstrumentCatalogItem item = checksummed(item("LIVE", NOW.minusSeconds(601), null, true));

        VenueRuleFreshness result = new VenueRuleFreshnessEvaluator(CLOCK, 600L).evaluate(item);

        assertEquals(VenueRuleFreshness.Availability.BLOCKED, result.availability());
        assertEquals(VenueRuleFreshness.FreshnessStatus.STALE, result.freshnessStatus());
        assertEquals("FRESH_UNTIL_EXCEEDED", result.blockingReason());
    }

    @Test
    void missingOrInvalidThresholdShouldBeUnknownAndBlocked() {
        InstrumentCatalogItem item = checksummed(item("LIVE", NOW.minusSeconds(30), null, true));

        for (Long threshold : new Long[]{null, 59L, 86_401L}) {
            VenueRuleFreshness result = new VenueRuleFreshnessEvaluator(CLOCK, threshold).evaluate(item);
            assertEquals(VenueRuleFreshness.Availability.BLOCKED, result.availability());
            assertEquals(VenueRuleFreshness.FreshnessStatus.UNKNOWN, result.freshnessStatus());
            assertEquals("STALE_AFTER_INVALID", result.blockingReason());
        }
    }

    @Test
    void nonLiveSnapshotShouldBeBlockedEvenWhenFactsAreComplete() {
        VenueRuleFreshness result = new VenueRuleFreshnessEvaluator(CLOCK, 600L)
                .evaluate(checksummed(item("SUSPEND", NOW.minusSeconds(30), null, true)));

        assertEquals(VenueRuleFreshness.Availability.BLOCKED, result.availability());
        assertEquals(VenueRuleFreshness.FreshnessStatus.UNKNOWN, result.freshnessStatus());
        assertEquals("INSTRUMENT_NOT_LIVE", result.blockingReason());
    }

    @Test
    void nextRuleEffectiveAtShouldTruncateFreshUntil() {
        Instant observedAt = NOW.minusSeconds(30);
        Instant nextEffectiveAt = NOW.plusSeconds(20);
        VenueRuleFreshness result = new VenueRuleFreshnessEvaluator(CLOCK, 600L)
                .evaluate(checksummed(item("LIVE", observedAt, nextEffectiveAt, true)));

        assertEquals(nextEffectiveAt, result.freshUntil());
        assertEquals(VenueRuleFreshness.FreshnessStatus.FRESH, result.freshnessStatus());
    }

    @Test
    void futureObservedAtShouldFailClosed() {
        VenueRuleFreshness result = new VenueRuleFreshnessEvaluator(CLOCK, 600L)
                .evaluate(checksummed(item("LIVE", NOW.plusSeconds(1), null, true)));

        assertEquals(VenueRuleFreshness.Availability.BLOCKED, result.availability());
        assertEquals(VenueRuleFreshness.FreshnessStatus.UNKNOWN, result.freshnessStatus());
        assertEquals("OBSERVED_AT_IN_FUTURE", result.blockingReason());
    }

    @Test
    void legacyNullFactsShouldRemainUnavailableAndUnknown() {
        InstrumentCatalogItem legacy = new InstrumentCatalogItem(
                "OKX", "SPOT", "BTC-USDT", "BTC-USDT", "BTC", "USDT", "LIVE",
                new BigDecimal("0.1"), new BigDecimal("0.001"), new BigDecimal("0.001"),
                "OKX_INSTRUMENTS_CACHE"
        );

        VenueRuleFreshness result = new VenueRuleFreshnessEvaluator(CLOCK, 600L).evaluate(legacy);

        assertEquals(VenueRuleFreshness.Availability.BLOCKED, result.availability());
        assertEquals(VenueRuleFreshness.FreshnessStatus.UNKNOWN, result.freshnessStatus());
    }

    private static InstrumentCatalogItem checksummed(InstrumentCatalogItem item) {
        String checksum = new VenueRuleChecksumCalculator().calculate(item);
        return copyWithChecksum(item, checksum);
    }

    private static InstrumentCatalogItem item(
            String status,
            Instant observedAt,
            Instant nextRuleEffectiveAt,
            boolean complete
    ) {
        return new InstrumentCatalogItem(
                null,
                "OKX",
                "SPOT",
                "BTC-USDT",
                "BTC-USDT",
                "BTC",
                "USDT",
                status,
                new BigDecimal("0.1"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.001"),
                complete ? new BigDecimal("100") : null,
                complete ? new BigDecimal("100000") : null,
                complete ? "USDT" : null,
                complete ? new BigDecimal("1000000") : null,
                complete ? new BigDecimal("1000000") : null,
                OkxVenueRuleContract.SOURCE,
                OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                observedAt,
                observedAt.plusSeconds(1),
                nextRuleEffectiveAt,
                null,
                null,
                null
        );
    }

    private static InstrumentCatalogItem copyWithChecksum(InstrumentCatalogItem item, String checksum) {
        return new InstrumentCatalogItem(
                item.instrumentId(), item.exchangeCode(), item.instrumentType(), item.exchangeSymbol(),
                item.internalSymbol(), item.baseAsset(), item.quoteAsset(), item.status(), item.tickSize(),
                item.stepSize(), item.minQuantity(), item.maxLimitQuantity(), item.maxMarketSize(),
                item.maxMarketSizeUnit(), item.maxLimitNotionalUsd(), item.maxMarketNotionalUsd(), item.source(),
                item.sourceSchemaVersion(), item.observedAt(), item.syncedAt(), item.nextRuleEffectiveAt(), checksum,
                item.createdAt(), item.updatedAt()
        );
    }
}
