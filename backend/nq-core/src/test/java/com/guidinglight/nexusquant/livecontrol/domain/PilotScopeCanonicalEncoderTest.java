package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PilotScopeCanonicalEncoderTest {

    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String C = "c".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-16T01:02:03.123456Z");

    @Test
    void shouldProduceFrozenGoldenPayloadAndDigest() {
        LiveSession session = session();
        PilotScopeBinding scope = scope(session, "").withCanonicalHash(session);

        String canonical = PilotScopeCanonicalEncoder.encode(session, scope);
        assertEquals(
                "be8cdd5153a053e10ed629d5b3932755b4e36cba31394ebf6e5c16f59d846741",
                scope.pilotScopeHash()
        );
        assertTrue(canonical.startsWith("{\"schemaVersion\":\"pilot-scope.v1\",\"sessionId\":"));
        assertTrue(canonical.endsWith("\"workerReleaseDigest\":\"" + B + "\"}"));
        assertEquals(scope.pilotScopeHash(), PilotScopeCanonicalEncoder.digest(session, scope));
    }

    @Test
    void shouldChangeHashForEveryVariableImmutableScopeField() {
        LiveSession session = session();
        PilotScopeBinding original = scope(session, "").withCanonicalHash(session);
        for (String field : List.of(
                "instrumentDigest", "instrumentSource", "instrumentSchema", "instrumentAge",
                "feeDigest", "feeTier", "feeEvidence", "feeSource", "feeSchema", "feeAge",
                "balanceSource", "balanceSchema", "balanceAge", "clockSource", "clockSchema",
                "clockAge", "skew", "endpointVersion", "endpointDigest", "providerIdentity",
                "providerDigest", "workerIdentity", "workerDigest")) {
            PilotScopeBinding changed = scope(session, field);
            assertNotEquals(original.pilotScopeHash(), PilotScopeCanonicalEncoder.digest(session, changed), field);
        }
    }

    @Test
    void shouldExcludeIdentityAuditAndFreshObservationFieldsFromScopeHash() {
        LiveSession session = session();
        PilotScopeBinding first = scope(session, "");
        PilotScopeBinding auditOnlyChange = new PilotScopeBinding(
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), first.sessionId(),
                first.instrumentMetadataDigest(), first.instrumentSourceIdentity(),
                first.instrumentSourceSchemaVersion(), first.instrumentMaximumAgeMs(),
                first.feeScheduleDigest(), first.feeTier(), first.feeEvidenceClass(),
                first.feeSourceIdentity(), first.feeSourceSchemaVersion(), first.feeMaximumAgeMs(),
                first.balanceSourceIdentity(), first.balanceSourceSchemaVersion(), first.balanceMaximumAgeMs(),
                first.clockSourceIdentity(), first.clockSourceSchemaVersion(), first.clockMaximumAgeMs(),
                first.signedTimestampSource(), first.maximumToleratedSkewMs(),
                first.endpointPolicyVersion(), first.endpointPolicyDigest(),
                first.providerContractIdentity(), first.providerArtifactDigest(),
                first.workerIdentity(), first.workerReleaseDigest(), C, 999, NOW.plusSeconds(60)
        );
        assertEquals(
                PilotScopeCanonicalEncoder.digest(session, first),
                PilotScopeCanonicalEncoder.digest(session, auditOnlyChange)
        );
    }

    @Test
    void shouldRejectNonCanonicalSymbolsTimeAndMoney() {
        assertThrows(IllegalArgumentException.class,
                () -> PilotScopeCanonicalEncoder.requireCanonicalSymbols(List.of("ETH-USDT", "BTC-USDT")));
        assertThrows(IllegalArgumentException.class,
                () -> PilotScopeCanonicalEncoder.requireCanonicalSymbols(List.of("BTC-USDT", "BTC-USDT")));
        assertThrows(IllegalArgumentException.class,
                () -> PilotScopeCanonicalEncoder.requireCanonicalSymbols(List.of("btc-usdt")));
        assertThrows(IllegalArgumentException.class, () -> CanonicalDigestSupport.instant(
                Instant.parse("2026-08-16T01:02:03.123456789Z")));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalDigestSupport.money(new BigDecimal("1.000000001"), "capitalCap"));
        assertEquals("\"1.00000000\"", CanonicalDigestSupport.decimal(new BigDecimal("1")));
    }

    private static LiveSession session() {
        return new LiveSession(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                101, 202, LiveSession.VENUE, "release-1", A, 7,
                UUID.fromString("22222222-2222-2222-2222-222222222222"), B, 303,
                List.of("BTC-USDT", "ETH-USDT"), new BigDecimal("25"),
                Instant.parse("2026-08-16T02:00:00Z"), Instant.parse("2026-08-16T02:05:00Z"),
                LiveSessionState.APPROVAL_PENDING, 1, C, 1, 101, NOW, NOW
        );
    }

    private static PilotScopeBinding scope(LiveSession session, String changed) {
        return new PilotScopeBinding(
                UUID.fromString("33333333-3333-3333-3333-333333333333"), session.id(),
                changed.equals("instrumentDigest") ? C : A,
                changed.equals("instrumentSource") ? "instrument-source-2" : "instrument-source-1",
                changed.equals("instrumentSchema") ? "instrument-source.v2" : "instrument-source.v1",
                changed.equals("instrumentAge") ? 2_001 : 2_000,
                changed.equals("feeDigest") ? A : B,
                changed.equals("feeTier") ? "tier-2" : "tier-1",
                changed.equals("feeEvidence")
                        ? PilotScopeBinding.FeeEvidenceClass.ESTIMATED_PUBLIC
                        : PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE,
                changed.equals("feeSource") ? "fee-source-2" : "fee-source-1",
                changed.equals("feeSchema") ? "fee-source.v2" : "fee-source.v1",
                changed.equals("feeAge") ? 60_001 : 60_000,
                changed.equals("balanceSource") ? "balance-source-2" : "balance-source-1",
                changed.equals("balanceSchema") ? "balance-source.v2" : "balance-source.v1",
                changed.equals("balanceAge") ? 2_001 : 2_000,
                changed.equals("clockSource") ? "clock-source-2" : "clock-source-1",
                changed.equals("clockSchema") ? "clock-source.v2" : "clock-source.v1",
                changed.equals("clockAge") ? 5_001 : 5_000,
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE,
                changed.equals("skew") ? 501 : 500,
                changed.equals("endpointVersion") ? "endpoint-policy.v2" : "endpoint-policy.v1",
                changed.equals("endpointDigest") ? B : A,
                changed.equals("providerIdentity") ? "provider-2" : "provider-1",
                changed.equals("providerDigest") ? C : A,
                changed.equals("workerIdentity") ? "worker-2" : "worker-1",
                changed.equals("workerDigest") ? A : B,
                C, 101, NOW
        );
    }
}
