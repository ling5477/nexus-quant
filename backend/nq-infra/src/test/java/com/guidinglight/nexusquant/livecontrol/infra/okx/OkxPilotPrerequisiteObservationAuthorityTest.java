package com.guidinglight.nexusquant.livecontrol.infra.okx;

import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPilotPrerequisiteRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPilotPrerequisiteSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxPilotPrerequisiteObservationAuthorityTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final String ZERO = "0".repeat(64);
    private static final long OWNER_ID = 7L;
    private static final long ACCOUNT_ID = 9L;
    private static final long CREDENTIAL_ID = 42L;

    @Test
    void createsCanonicalCompleteV2ObservationSetFromExactJitCredentialScope() {
        CapturingExecutor executor = new CapturingExecutor(snapshot(0));
        OkxPilotPrerequisiteObservationAuthority authority =
                new OkxPilotPrerequisiteObservationAuthority(executor);

        PilotObservationSet first = authority.resolveTrustedObservationSet(session(), scope(), NOW);
        PilotObservationSet same = authority.resolveTrustedObservationSet(session(), scope(), NOW);

        assertEquals(first, same, "same server facts and collection time must produce deterministic identities");
        assertEquals(2, executor.calls.get());
        assertEquals(OWNER_ID, executor.ownerId.get());
        assertEquals(ACCOUNT_ID, executor.accountId.get());
        assertEquals(CREDENTIAL_ID, executor.credentialId.get());
        assertEquals(List.of("BTC-USDT"), executor.lastRequest.instruments());
        assertEquals(PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION,
                first.instrumentMetadata().envelope().observationSchemaVersion());
        var item = first.instrumentMetadata().items().get(0);
        assertEquals(PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_NOT_PUBLISHED,
                item.minimumOrderValueEvidenceClass());
        assertNull(item.minimumOrderValue());
        assertNull(item.minimumOrderValueCurrency());
        assertEquals(PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE,
                first.feeSchedule().feeEvidenceClass());
        assertEquals("Lv1/1", first.feeSchedule().feeTier());
        assertEquals(0, new BigDecimal("100.25").compareTo(first.balanceSnapshot().availableBalance()));
        assertEquals(0, first.clockSync().observedSkewMs());
        assertTrue(first.observations().stream().allMatch(value ->
                value.envelope().observedAt().equals(NOW)
                        && value.envelope().recordedAt().equals(NOW)
                        && value.envelope().recorderIdentity().equals("worker-1")
                        && PilotObservationCanonicalEncoder.digest(value)
                        .equals(value.observationPayloadHash())));
        assertFalse(OkxPilotPrerequisiteObservationAuthority.class.isAnnotationPresent(Component.class));
    }

    @Test
    void rejectsSourceMismatchBeforeCredentialReadAndReturnsOnlySanitizedError() {
        String marker = "secret-provider-marker";
        CapturingExecutor executor = new CapturingExecutor(snapshot(0));
        OkxPilotPrerequisiteObservationAuthority authority =
                new OkxPilotPrerequisiteObservationAuthority(executor);

        LiveControlException failure = assertThrows(LiveControlException.class,
                () -> authority.resolveTrustedObservationSet(session(), scope("WRONG_CLOCK_SOURCE"), NOW));

        assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE", failure.code());
        assertEquals(0, executor.calls.get());
        assertFalse(failure.getMessage().contains(marker));
        assertFalse(failure.toString().contains(marker));
    }

    @Test
    void anyMalformedOrPartialCollectionFailsAsOneSetWithoutReturningFacts() {
        CapturingExecutor executor = new CapturingExecutor(snapshot(1_001));
        OkxPilotPrerequisiteObservationAuthority authority =
                new OkxPilotPrerequisiteObservationAuthority(executor);

        LiveControlException failure = assertThrows(LiveControlException.class,
                () -> authority.resolveTrustedObservationSet(session(), scope(), NOW));

        assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE", failure.code());
        assertEquals(1, executor.calls.get());
    }

    @Test
    void rejectsStaleProviderFactsAndCollectionsThatOutliveTheShortestFreshnessWindow() {
        OkxPilotPrerequisiteObservationAuthority staleFeeAuthority =
                new OkxPilotPrerequisiteObservationAuthority(new CapturingExecutor(
                        snapshot(0, NOW.minusSeconds(61), NOW)));
        OkxPilotPrerequisiteObservationAuthority staleCollectionAuthority =
                new OkxPilotPrerequisiteObservationAuthority(new CapturingExecutor(
                        snapshot(0, NOW, NOW.plusMillis(5_001))));

        LiveControlException staleFee = assertThrows(LiveControlException.class,
                () -> staleFeeAuthority.resolveTrustedObservationSet(session(), scope(), NOW));
        LiveControlException staleCollection = assertThrows(LiveControlException.class,
                () -> staleCollectionAuthority.resolveTrustedObservationSet(session(), scope(), NOW));

        assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE", staleFee.code());
        assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE", staleCollection.code());
    }

    private static LiveSession session() {
        return LiveSession.create(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                OWNER_ID,
                ACCOUNT_ID,
                "strategy-release-1",
                "1".repeat(64),
                1,
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "2".repeat(64),
                CREDENTIAL_ID,
                List.of("BTC-USDT"),
                new BigDecimal("100"),
                NOW,
                NOW.plusSeconds(60),
                OWNER_ID,
                NOW
        );
    }

    private static PilotScopeBinding scope() {
        return scope(OkxPilotPrerequisiteObservationAuthority.CLOCK_SOURCE);
    }

    private static PilotScopeBinding scope(String clockSource) {
        List<PilotPrerequisiteObservation.InstrumentItem> items = List.of(
                new PilotPrerequisiteObservation.InstrumentItem(
                        "BTC-USDT", PilotPrerequisiteObservation.TradingStatus.LIVE,
                        new BigDecimal("0.1"), new BigDecimal("0.0001"), new BigDecimal("0.1"),
                        PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_NOT_PUBLISHED,
                        null, null));
        String instrumentDigest = PilotObservationCanonicalEncoder.instrumentMetadataDigest(items);
        String feeDigest = PilotObservationCanonicalEncoder.feeScheduleDigest(
                List.of("BTC-USDT"), "Lv1/1", new BigDecimal("-0.0008"), new BigDecimal("-0.001"));
        return new PilotScopeBinding(
                UUID.fromString("99999999-8888-7777-6666-555555555555"),
                session().id(),
                instrumentDigest,
                OkxPilotPrerequisiteObservationAuthority.INSTRUMENT_SOURCE,
                OkxPilotPrerequisiteObservationAuthority.INSTRUMENT_SOURCE_SCHEMA,
                30_000,
                feeDigest,
                "Lv1/1",
                PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE,
                OkxPilotPrerequisiteObservationAuthority.FEE_SOURCE,
                OkxPilotPrerequisiteObservationAuthority.FEE_SOURCE_SCHEMA,
                60_000,
                OkxPilotPrerequisiteObservationAuthority.BALANCE_SOURCE,
                OkxPilotPrerequisiteObservationAuthority.BALANCE_SOURCE_SCHEMA,
                5_000,
                clockSource,
                OkxPilotPrerequisiteObservationAuthority.CLOCK_SOURCE_SCHEMA,
                5_000,
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE,
                1_000,
                "endpoint-policy.v1", "3".repeat(64),
                "okx-spot-provider.v1", "4".repeat(64),
                "worker-1", "5".repeat(64),
                ZERO, OWNER_ID, NOW
        );
    }

    private static OkxPilotPrerequisiteSnapshot snapshot(long skew) {
        return snapshot(skew, NOW, NOW);
    }

    private static OkxPilotPrerequisiteSnapshot snapshot(
            long skew,
            Instant feeProviderTimestamp,
            Instant localClockMidpoint
    ) {
        return new OkxPilotPrerequisiteSnapshot(
                List.of(new OkxPilotPrerequisiteSnapshot.InstrumentFact(
                        "BTC-USDT", "live", "1", new BigDecimal("0.1"),
                        new BigDecimal("0.0001"), new BigDecimal("0.1"))),
                List.of(new OkxPilotPrerequisiteSnapshot.FeeFact(
                        "BTC-USDT", "Lv1", "1", new BigDecimal("-0.0008"),
                        new BigDecimal("-0.001"), feeProviderTimestamp)),
                new BigDecimal("100.25"), localClockMidpoint.plusMillis(skew), localClockMidpoint, skew
        );
    }

    private static final class CapturingExecutor implements OkxPrivateCredentialExecutor {
        private final OkxPilotPrerequisiteSnapshot snapshot;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicLong ownerId = new AtomicLong();
        private final AtomicLong accountId = new AtomicLong();
        private final AtomicLong credentialId = new AtomicLong();
        private OkxPilotPrerequisiteRequest lastRequest;

        private CapturingExecutor(OkxPilotPrerequisiteSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public <T> T withActiveCredential(
                Long ownerId,
                Long exchangeAccountId,
                String credentialType,
                CredentialCallback<T> callback
        ) {
            throw new AssertionError("authority must use exact credential reference");
        }

        @Override
        public <T> T withActiveCredential(
                Long ownerId,
                Long exchangeAccountId,
                Long credentialId,
                String credentialType,
                CredentialCallback<T> callback
        ) {
            calls.incrementAndGet();
            this.ownerId.set(ownerId);
            accountId.set(exchangeAccountId);
            this.credentialId.set(credentialId);
            return callback.execute(new CredentialSession() {
                @Override
                public OkxPrivateReadResult execute(
                        OkxPrivateReadRequest request,
                        OkxPrivateEnvironment environment
                ) {
                    throw new AssertionError("legacy read path must not be used");
                }

                @Override
                public OkxPilotPrerequisiteSnapshot observePrerequisites(
                        OkxPilotPrerequisiteRequest request,
                        OkxPrivateEnvironment environment
                ) {
                    assertEquals(OkxPrivateEnvironment.PRODUCTION, environment);
                    lastRequest = request;
                    return snapshot;
                }
            });
        }
    }
}
