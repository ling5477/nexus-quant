package com.guidinglight.nexusquant.marketdata.domain.instrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class VenueRuleChecksumCalculatorTest {

    private final VenueRuleChecksumCalculator calculator = new VenueRuleChecksumCalculator();

    @Test
    void shouldUseFixedFieldOrderAndNullRepresentation() {
        String canonical = calculator.canonicalDocument(item(
                "0.10",
                null,
                OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                Instant.parse("2026-07-13T08:00:00Z")
        ));

        assertTrue(canonical.startsWith("{\"sourceSchemaVersion\":\"NQ_OKX_VENUE_RULE_FACTS_V1\","));
        assertTrue(canonical.contains("\"tickSz\":\"0.1\""));
        assertTrue(canonical.contains("\"maxLmtSz\":null"));
        assertTrue(canonical.endsWith("\"nextRuleEffectiveAt\":\"2026-07-14T00:00:00Z\"}"));
    }

    @Test
    void equivalentDecimalsAndOperationalTimestampsShouldProduceSameChecksum() {
        InstrumentCatalogItem first = item(
                "0.1000",
                "100.000",
                OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                Instant.parse("2026-07-13T08:00:00Z")
        );
        InstrumentCatalogItem second = item(
                "0.1",
                "100",
                OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                Instant.parse("2026-07-13T09:00:00Z")
        );

        assertEquals(calculator.calculate(first), calculator.calculate(second));
        assertEquals(64, calculator.calculate(first).length());
    }

    @Test
    void schemaVersionShouldParticipateInChecksum() {
        String accepted = calculator.calculate(item(
                "0.1",
                "100",
                OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                Instant.parse("2026-07-13T08:00:00Z")
        ));
        String changed = calculator.calculate(item(
                "0.1",
                "100",
                "OKX_PUBLIC_INSTRUMENTS_V5_NEXT",
                Instant.parse("2026-07-13T08:00:00Z")
        ));

        assertNotEquals(accepted, changed);
    }

    @Test
    void nextRuleEffectiveAtShouldParticipateAsPlannedChangeIntegrityFact() {
        InstrumentCatalogItem original = item(
                "0.1",
                "100",
                OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                Instant.parse("2026-07-13T08:00:00Z")
        );
        InstrumentCatalogItem changed = copyWithNextRuleEffectiveAt(
                original,
                Instant.parse("2026-07-14T01:00:00Z")
        );

        assertNotEquals(calculator.calculate(original), calculator.calculate(changed));
    }

    private static InstrumentCatalogItem item(
            String tickSize,
            String maximumLimitQuantity,
            String schemaVersion,
            Instant observedAt
    ) {
        return new InstrumentCatalogItem(
                9L,
                "OKX",
                "SPOT",
                "BTC-USDT",
                "BTC-USDT",
                "BTC",
                "USDT",
                "LIVE",
                new BigDecimal(tickSize),
                new BigDecimal("0.0001"),
                new BigDecimal("0.001"),
                maximumLimitQuantity == null ? null : new BigDecimal(maximumLimitQuantity),
                new BigDecimal("100000"),
                "USDT",
                new BigDecimal("1000000"),
                new BigDecimal("1000000"),
                OkxVenueRuleContract.SOURCE,
                schemaVersion,
                observedAt,
                observedAt.plusSeconds(1),
                Instant.parse("2026-07-14T00:00:00Z"),
                null,
                observedAt.minusSeconds(10),
                observedAt.plusSeconds(1)
        );
    }

    private static InstrumentCatalogItem copyWithNextRuleEffectiveAt(
            InstrumentCatalogItem item,
            Instant nextRuleEffectiveAt
    ) {
        return new InstrumentCatalogItem(
                item.instrumentId(),
                item.exchangeCode(),
                item.instrumentType(),
                item.exchangeSymbol(),
                item.internalSymbol(),
                item.baseAsset(),
                item.quoteAsset(),
                item.status(),
                item.tickSize(),
                item.stepSize(),
                item.minQuantity(),
                item.maxLimitQuantity(),
                item.maxMarketSize(),
                item.maxMarketSizeUnit(),
                item.maxLimitNotionalUsd(),
                item.maxMarketNotionalUsd(),
                item.source(),
                item.sourceSchemaVersion(),
                item.observedAt(),
                item.syncedAt(),
                nextRuleEffectiveAt,
                item.ruleChecksum(),
                item.createdAt(),
                item.updatedAt()
        );
    }
}
