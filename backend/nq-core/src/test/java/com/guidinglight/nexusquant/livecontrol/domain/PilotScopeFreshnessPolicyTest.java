package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PilotScopeFreshnessPolicyTest {

    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-16T02:00:00Z");

    @Test
    void shouldFailClosedForStaleInsufficientNonLiveOrExcessiveSkewFacts() {
        Fixture fixture = fixture(NOW.minusSeconds(10), new BigDecimal("5"),
                PilotPrerequisiteObservation.TradingStatus.SUSPEND,
                PilotScopeBinding.FeeEvidenceClass.ESTIMATED_PUBLIC, 501);
        PilotScopePreflightResult result = new PilotScopeFreshnessPolicy().evaluate(
                fixture.scope(), fixture.observations(), new BigDecimal("25"), NOW);

        assertFalse(result.eligible());
        assertTrue(result.violations().contains(PilotScopePreflightResult.Violation.BALANCE_STALE));
        assertTrue(result.violations().contains(PilotScopePreflightResult.Violation.INSTRUMENT_NOT_LIVE));
        assertTrue(result.violations().contains(PilotScopePreflightResult.Violation.FEE_NOT_OBSERVED_PRIVATE));
        assertTrue(result.violations().contains(PilotScopePreflightResult.Violation.BALANCE_INSUFFICIENT));
        assertTrue(result.violations().contains(PilotScopePreflightResult.Violation.CLOCK_SKEW_EXCEEDED));
    }

    private static Fixture fixture(
            Instant observedAt,
            BigDecimal balance,
            PilotPrerequisiteObservation.TradingStatus status,
            PilotScopeBinding.FeeEvidenceClass feeEvidenceClass,
            long skew
    ) {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID scopeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID setId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        var item = new PilotPrerequisiteObservation.InstrumentItem(
                "BTC-USDT", status, new BigDecimal("0.1"), new BigDecimal("0.001"),
                new BigDecimal("0.001"), new BigDecimal("5"), "USDT");
        String instrumentDigest = PilotObservationCanonicalEncoder.instrumentMetadataDigest(List.of(item));
        PilotScopeBinding scope = new PilotScopeBinding(
                scopeId, sessionId, instrumentDigest, "instrument-source", "instrument.v1", 2_000,
                B, "tier-1", feeEvidenceClass, "fee-source", "fee.v1", 60_000,
                "balance-source", "balance.v1", 2_000, "clock-source", "clock.v1", 5_000,
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, 500, "endpoint.v1", A,
                "provider", A, "worker", B, A, 1, NOW
        );
        var instrument = canonical(new PilotPrerequisiteObservation.InstrumentMetadata(
                envelope(scopeId, setId, "i", "instrument-source", "instrument.v1",
                        PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION, observedAt),
                instrumentDigest, List.of(item)));
        var fee = canonical(new PilotPrerequisiteObservation.FeeSchedule(
                envelope(scopeId, setId, "f", "fee-source", "fee.v1",
                        PilotPrerequisiteObservation.FeeSchedule.SCHEMA_VERSION, observedAt),
                B, "tier-1", feeEvidenceClass, new BigDecimal("0.001"), new BigDecimal("0.001"),
                PilotPrerequisiteObservation.FeeSchedule.LOSS_TREATMENT));
        var balanceObservation = canonical(new PilotPrerequisiteObservation.BalanceSnapshot(
                envelope(scopeId, setId, "b", "balance-source", "balance.v1",
                        PilotPrerequisiteObservation.BalanceSnapshot.SCHEMA_VERSION, observedAt),
                A, "USDT", balance));
        var clock = canonical(new PilotPrerequisiteObservation.ClockSync(
                envelope(scopeId, setId, "c", "clock-source", "clock.v1",
                        PilotPrerequisiteObservation.ClockSync.SCHEMA_VERSION, observedAt),
                B, PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, skew));
        return new Fixture(scope, new PilotObservationSet(setId, scopeId, instrument, fee, balanceObservation, clock));
    }

    private static PilotPrerequisiteObservation.Envelope envelope(
            UUID scopeId, UUID setId, String identity, String source, String sourceSchema,
            String observationSchema, Instant observedAt
    ) {
        return new PilotPrerequisiteObservation.Envelope(
                UUID.randomUUID(), scopeId, setId, observationSchema, identity, source, sourceSchema,
                observedAt, NOW, "worker", "0".repeat(64));
    }

    private static PilotPrerequisiteObservation.InstrumentMetadata canonical(
            PilotPrerequisiteObservation.InstrumentMetadata value
    ) {
        var canonical = new PilotPrerequisiteObservation.InstrumentMetadata(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.instrumentMetadataDigest(), value.items());
        return canonical;
    }

    private static PilotPrerequisiteObservation.FeeSchedule canonical(
            PilotPrerequisiteObservation.FeeSchedule value
    ) {
        return new PilotPrerequisiteObservation.FeeSchedule(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.feeScheduleDigest(), value.feeTier(), value.feeEvidenceClass(),
                value.makerFeeRate(), value.takerFeeRate(), value.feeLossTreatment());
    }

    private static PilotPrerequisiteObservation.BalanceSnapshot canonical(
            PilotPrerequisiteObservation.BalanceSnapshot value
    ) {
        return new PilotPrerequisiteObservation.BalanceSnapshot(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.balanceSnapshotDigest(), value.balanceCurrency(), value.availableBalance());
    }

    private static PilotPrerequisiteObservation.ClockSync canonical(
            PilotPrerequisiteObservation.ClockSync value
    ) {
        return new PilotPrerequisiteObservation.ClockSync(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.clockSyncObservationDigest(), value.signedTimestampSource(), value.observedSkewMs());
    }

    private record Fixture(PilotScopeBinding scope, PilotObservationSet observations) {
    }
}
