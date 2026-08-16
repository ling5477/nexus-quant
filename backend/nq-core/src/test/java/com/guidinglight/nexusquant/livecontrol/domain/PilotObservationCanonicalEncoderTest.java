package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PilotObservationCanonicalEncoderTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-16T04:05:06.123456Z");

    @Test
    void shouldKeepHistoricalV1CanonicalBytesUnchanged() {
        var item = item(
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.LEGACY_V40_REQUIRED,
                new BigDecimal("5.000"), "USDT");
        String expectedPayload = "{\"schemaVersion\":\"instrument-metadata-observation.v1\",\"items\":[" +
                "{\"symbol\":\"BTC-USDT\",\"tradingStatus\":\"LIVE\",\"tickSize\":\"0.1\"," +
                "\"lotSize\":\"0.001\",\"minimumOrderSize\":\"0.001\"," +
                "\"minimumOrderValue\":\"5\",\"minimumOrderValueCurrency\":\"USDT\"}]}";

        assertEquals(CanonicalDigestSupport.sha256(expectedPayload),
                PilotObservationCanonicalEncoder.instrumentMetadataDigest(
                        PilotPrerequisiteObservation.InstrumentMetadata.LEGACY_SCHEMA_VERSION, List.of(item)));
    }

    @Test
    void shouldEncodeVenueNotPublishedWithoutNullableValueFieldsDeterministically() {
        var item = item(
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_NOT_PUBLISHED, null, null);
        String digest = PilotObservationCanonicalEncoder.instrumentMetadataDigest(List.of(item));
        var draft = new PilotPrerequisiteObservation.InstrumentMetadata(
                envelope(PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION), digest, List.of(item));
        String encoded = PilotObservationCanonicalEncoder.encode(draft);

        assertTrue(encoded.contains("\"minimumOrderValueEvidenceClass\":\"VENUE_NOT_PUBLISHED\""));
        assertFalse(encoded.contains("\"minimumOrderValue\":"));
        assertFalse(encoded.contains("\"minimumOrderValueCurrency\":"));
        assertEquals(digest, PilotObservationCanonicalEncoder.instrumentMetadataDigest(List.of(item)));
    }

    @Test
    void shouldRejectFakeOrIncompleteMinimumOrderValueEvidence() {
        assertThrows(IllegalArgumentException.class, () -> item(
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_NOT_PUBLISHED,
                BigDecimal.ONE, "USDT"));
        assertThrows(NullPointerException.class, () -> item(
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_PUBLISHED,
                null, "USDT"));
        assertThrows(IllegalArgumentException.class, () -> item(
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_PUBLISHED,
                BigDecimal.ONE, null));
    }

    @Test
    void shouldKeepLegacyEvidenceOutOfV2Observations() {
        var legacy = item(
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.LEGACY_V40_REQUIRED,
                new BigDecimal("5"), "USDT");
        String digest = PilotObservationCanonicalEncoder.instrumentMetadataDigest(
                PilotPrerequisiteObservation.InstrumentMetadata.LEGACY_SCHEMA_VERSION, List.of(legacy));

        assertThrows(IllegalArgumentException.class, () -> new PilotPrerequisiteObservation.InstrumentMetadata(
                envelope(PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION), digest, List.of(legacy)));
    }

    private static PilotPrerequisiteObservation.InstrumentItem item(
            PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass evidenceClass,
            BigDecimal minimumOrderValue,
            String currency
    ) {
        return new PilotPrerequisiteObservation.InstrumentItem(
                "BTC-USDT", PilotPrerequisiteObservation.TradingStatus.LIVE,
                new BigDecimal("0.1"), new BigDecimal("0.001"), new BigDecimal("0.001"),
                evidenceClass, minimumOrderValue, currency);
    }

    private static PilotPrerequisiteObservation.Envelope envelope(String schemaVersion) {
        return new PilotPrerequisiteObservation.Envelope(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                schemaVersion, "instrument-observation-1", "venue-instrument-source",
                "venue-instrument-source.v1", OBSERVED_AT, OBSERVED_AT,
                "worker-release-1", "0".repeat(64));
    }
}
