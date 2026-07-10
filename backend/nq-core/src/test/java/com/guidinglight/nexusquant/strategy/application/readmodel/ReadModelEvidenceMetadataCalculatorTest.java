package com.guidinglight.nexusquant.strategy.application.readmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** 验证统一 evidence metadata 的固定 Clock 与 fail-closed 语义。 */
class ReadModelEvidenceMetadataCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ReadModelEvidenceMetadataCalculator CALCULATOR =
            new ReadModelEvidenceMetadataCalculator(FIXED_CLOCK);

    @Test
    void shouldReturnFreshForAvailableEvidenceInsideThreshold() {
        ReadModelEvidenceMetadata metadata = CALCULATOR.calculate(
                "LOCAL_DB_TEST",
                Availability.AVAILABLE,
                NOW.minusSeconds(60),
                Duration.ofMinutes(5)
        );

        assertEquals(FreshnessStatus.FRESH, metadata.freshnessStatus());
        assertEquals(60L, metadata.ageSeconds());
        assertEquals(300L, metadata.staleAfterSeconds());
        assertNull(metadata.staleReason());
    }

    @Test
    void shouldReturnStaleWhenThresholdIsExceeded() {
        ReadModelEvidenceMetadata metadata = CALCULATOR.calculate(
                "LOCAL_DB_TEST",
                Availability.AVAILABLE,
                NOW.minusSeconds(301),
                Duration.ofMinutes(5)
        );

        assertEquals(FreshnessStatus.STALE, metadata.freshnessStatus());
        assertEquals("STALE_THRESHOLD_EXCEEDED", metadata.staleReason());
    }

    @Test
    void shouldReturnUnknownWhenTimestampIsMissing() {
        ReadModelEvidenceMetadata metadata = CALCULATOR.calculate(
                "LOCAL_DB_TEST",
                Availability.UNAVAILABLE,
                null,
                Duration.ofMinutes(5)
        );

        assertEquals(FreshnessStatus.UNKNOWN, metadata.freshnessStatus());
        assertNull(metadata.lastCalculatedAt());
        assertNull(metadata.ageSeconds());
    }

    @Test
    void shouldNeverReturnFreshForUnavailableOrPartialEvidence() {
        for (Availability availability : new Availability[]{Availability.UNAVAILABLE, Availability.PARTIAL}) {
            ReadModelEvidenceMetadata metadata = CALCULATOR.calculate(
                    "LOCAL_DB_TEST",
                    availability,
                    NOW.minusSeconds(60),
                    Duration.ofMinutes(5)
            );

            assertEquals(FreshnessStatus.UNKNOWN, metadata.freshnessStatus());
            assertEquals("SOURCE_" + availability.name(), metadata.staleReason());
        }
    }

    @Test
    void shouldKeepUnknownWhenNoAuthoritativeThresholdExists() {
        ReadModelEvidenceMetadata metadata = CALCULATOR.calculate(
                "LOCAL_DB_TEST",
                Availability.AVAILABLE,
                NOW.minusSeconds(60),
                null
        );

        assertEquals(FreshnessStatus.UNKNOWN, metadata.freshnessStatus());
        assertEquals(60L, metadata.ageSeconds());
        assertNull(metadata.staleAfterSeconds());
        assertEquals("STALE_THRESHOLD_NOT_DEFINED", metadata.staleReason());
    }

    @Test
    void shouldRejectFutureTimestampWithoutNegativeAge() {
        ReadModelEvidenceMetadata metadata = CALCULATOR.calculate(
                "LOCAL_DB_TEST",
                Availability.AVAILABLE,
                NOW.plusSeconds(1),
                Duration.ofMinutes(5)
        );

        assertEquals(FreshnessStatus.UNKNOWN, metadata.freshnessStatus());
        assertNull(metadata.ageSeconds());
        assertEquals("LAST_CALCULATED_AT_IN_FUTURE", metadata.staleReason());
    }

    @Test
    void shouldRejectBlankSourceInsteadOfInventingFactOrigin() {
        assertThrows(IllegalArgumentException.class, () -> CALCULATOR.calculate(
                " ",
                Availability.AVAILABLE,
                NOW.minusSeconds(60),
                Duration.ofMinutes(5)
        ));
    }
}
