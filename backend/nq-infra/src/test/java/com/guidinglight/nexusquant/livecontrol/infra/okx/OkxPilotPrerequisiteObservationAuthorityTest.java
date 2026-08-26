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
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogRepository;
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
    private static final String RELEASE_ID = "1".repeat(40);
    private static final String MANIFEST_SHA256 = "2".repeat(64);

    @Test
    void createsCanonicalCompleteV2ObservationSetFromExactJitCredentialScope() {
        CapturingExecutor executor = new CapturingExecutor(snapshot(0));
        InMemoryCatalogRepository catalog = new InMemoryCatalogRepository();
        OkxPilotPrerequisiteObservationAuthority authority = authority(executor, catalog);

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
        assertEquals(1, catalog.items.size());
        assertEquals("BTC-USDT", catalog.items.getFirst().exchangeSymbol());
        assertTrue(first.observations().stream().allMatch(value ->
                value.envelope().observedAt().equals(NOW)
                        && value.envelope().recordedAt().equals(NOW)
                        && value.envelope().recorderIdentity().equals("worker-1")
                        && PilotObservationCanonicalEncoder.digest(value)
                        .equals(value.observationPayloadHash())));
        assertFalse(OkxPilotPrerequisiteObservationAuthority.class.isAnnotationPresent(Component.class));
    }

    @Test
    void bootstrapsOperatorScopeAndObservationsFromExactlyOneTrustedSnapshot() {
        CapturingExecutor executor = new CapturingExecutor(snapshot(0));
        OkxPilotPrerequisiteObservationAuthority authority = authority(executor);
        LiveSession session = operatorSession();
        UUID pilotScopeId = UUID.fromString("99999999-8888-7777-6666-555555555555");

        var bootstrap = authority.bootstrapTrustedOperatorPilotScope(
                session, pilotScopeId, OWNER_ID, NOW);

        PilotScopeBinding scope = bootstrap.scopeBinding();
        PilotObservationSet observations = bootstrap.observationSet();
        assertEquals(1, executor.calls.get());
        assertEquals(pilotScopeId, scope.id());
        assertEquals(session.id(), scope.sessionId());
        assertTrue(scope.hasCanonicalHash(session));
        assertEquals(observations.instrumentMetadata().instrumentMetadataDigest(),
                scope.instrumentMetadataDigest());
        assertEquals(observations.feeSchedule().feeScheduleDigest(), scope.feeScheduleDigest());
        assertEquals(observations.feeSchedule().feeTier(), scope.feeTier());
        assertEquals(MANIFEST_SHA256, scope.providerArtifactDigest());
        assertEquals(MANIFEST_SHA256, scope.workerReleaseDigest());
        assertEquals("gatey-minimal-live-pilot@" + RELEASE_ID, scope.workerIdentity());
        assertEquals(OkxPilotPrerequisiteObservationAuthority.OPERATOR_ENDPOINT_POLICY_VERSION,
                scope.endpointPolicyVersion());
        assertEquals("d6c5aba2968ae54bc54d3285214aec144be80982728f2ccfe6e8046c17d1a886",
                scope.endpointPolicyDigest());
        assertEquals(OkxPilotPrerequisiteObservationAuthority.OPERATOR_MAXIMUM_TOLERATED_SKEW_MS,
                scope.maximumToleratedSkewMs());
    }

    @Test
    void operatorBootstrapUsesVerifiedCollectionMidpointAsCommonRecordedAt() {
        Instant collectionMidpoint = NOW.plusMillis(750);
        OkxPilotPrerequisiteObservationAuthority authority = authority(
                new CapturingExecutor(snapshot(0, NOW, collectionMidpoint)));

        var bootstrap = authority.bootstrapTrustedOperatorPilotScope(
                operatorSession(), UUID.randomUUID(), OWNER_ID, NOW);

        assertTrue(bootstrap.observationSet().observations().stream().allMatch(observation ->
                observation.envelope().recordedAt().equals(collectionMidpoint)));
        assertEquals(NOW, bootstrap.observationSet().instrumentMetadata().envelope().observedAt());
        assertEquals(collectionMidpoint,
                bootstrap.observationSet().marketSnapshot().envelope().observedAt());
    }

    @Test
    void operatorBootstrapRejectsExcessiveSkewBeforeCatalogWrite() {
        CapturingExecutor executor = new CapturingExecutor(snapshot(
                OkxPilotPrerequisiteObservationAuthority.OPERATOR_MAXIMUM_TOLERATED_SKEW_MS + 1));
        InMemoryCatalogRepository catalog = new InMemoryCatalogRepository();
        OkxPilotPrerequisiteObservationAuthority authority = authority(executor, catalog);

        LiveControlException failure = assertThrows(
                LiveControlException.class,
                () -> authority.bootstrapTrustedOperatorPilotScope(
                        operatorSession(), UUID.randomUUID(), OWNER_ID, NOW));

        assertEquals("TRUSTED_OPERATOR_PILOT_SCOPE_BOOTSTRAP_FRESHNESS_FAILED", failure.code());
        assertEquals(1, executor.calls.get());
        assertTrue(catalog.items.isEmpty());
    }

    @Test
    void operatorBootstrapRoundsAvailableBalanceDownToCanonicalMoneyScale() {
        OkxPilotPrerequisiteObservationAuthority authority = authority(
                new CapturingExecutor(snapshot(0, new BigDecimal("10.123456789"))));

        var bootstrap = authority.bootstrapTrustedOperatorPilotScope(
                operatorSession(), UUID.randomUUID(), OWNER_ID, NOW);

        assertEquals(new BigDecimal("10.12345678"),
                bootstrap.observationSet().balanceSnapshot().availableBalance());
    }

    @Test
    void rejectsSourceMismatchBeforeCredentialReadAndReturnsOnlySanitizedError() {
        String marker = "secret-provider-marker";
        CapturingExecutor executor = new CapturingExecutor(snapshot(0));
        OkxPilotPrerequisiteObservationAuthority authority = authority(executor);

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
        OkxPilotPrerequisiteObservationAuthority authority = authority(executor);

        LiveControlException failure = assertThrows(LiveControlException.class,
                () -> authority.resolveTrustedObservationSet(session(), scope(), NOW));

        assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE", failure.code());
        assertEquals(1, executor.calls.get());
    }

    @Test
    void rejectsStaleProviderFactsAndCollectionsThatOutliveTheShortestFreshnessWindow() {
        OkxPilotPrerequisiteObservationAuthority staleFeeAuthority =
                authority(new CapturingExecutor(snapshot(0, NOW.minusSeconds(61), NOW)));
        OkxPilotPrerequisiteObservationAuthority staleCollectionAuthority =
                authority(new CapturingExecutor(snapshot(0, NOW, NOW.plusMillis(5_001))));

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

    private static LiveSession operatorSession() {
        var authority = com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority.active(
                UUID.fromString("22222222-3333-4444-5555-666666666666"),
                OWNER_ID, ACCOUNT_ID, CREDENTIAL_ID, "BTC-USDT",
                com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority.Side.BUY,
                com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority.OrderType.LIMIT,
                new BigDecimal("10.00000000"), NOW, NOW.plusSeconds(120), OWNER_ID, NOW);
        return LiveSession.createOperatorPilot(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                OWNER_ID, ACCOUNT_ID, authority.id(), authority.canonicalDigest(), CREDENTIAL_ID,
                "BTC-USDT", authority.maxNotional(), NOW, NOW.plusSeconds(120), OWNER_ID, NOW);
    }

    private static OkxPilotPrerequisiteObservationAuthority authority(CapturingExecutor executor) {
        return authority(executor, new InMemoryCatalogRepository());
    }

    private static OkxPilotPrerequisiteObservationAuthority authority(
            CapturingExecutor executor,
            InMemoryCatalogRepository catalog
    ) {
        return new OkxPilotPrerequisiteObservationAuthority(
                executor, new InstrumentCatalogService(catalog), RELEASE_ID, MANIFEST_SHA256);
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

    private static OkxPilotPrerequisiteSnapshot snapshot(long skew, BigDecimal availableBalance) {
        return snapshot(skew, NOW, NOW, availableBalance);
    }

    private static OkxPilotPrerequisiteSnapshot snapshot(
            long skew,
            Instant feeProviderTimestamp,
            Instant localClockMidpoint
    ) {
        return snapshot(skew, feeProviderTimestamp, localClockMidpoint, new BigDecimal("100.25"));
    }

    private static OkxPilotPrerequisiteSnapshot snapshot(
            long skew,
            Instant feeProviderTimestamp,
            Instant localClockMidpoint,
            BigDecimal availableBalance
    ) {
        return new OkxPilotPrerequisiteSnapshot(
                List.of(new OkxPilotPrerequisiteSnapshot.InstrumentFact(
                        "BTC-USDT", "live", "1", new BigDecimal("0.1"),
                        new BigDecimal("0.0001"), new BigDecimal("0.1"))),
                List.of(new OkxPilotPrerequisiteSnapshot.FeeFact(
                        "BTC-USDT", "Lv1", "1", new BigDecimal("-0.0008"),
                        new BigDecimal("-0.001"), feeProviderTimestamp)),
                List.of(new OkxPilotPrerequisiteSnapshot.MarketFact("BTC-USDT",new BigDecimal("100"),localClockMidpoint)),
                availableBalance, localClockMidpoint.plusMillis(skew), localClockMidpoint, skew
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

    private static final class InMemoryCatalogRepository implements InstrumentCatalogRepository {
        private List<InstrumentCatalogItem> items = List.of();

        @Override
        public List<InstrumentCatalogItem> list(String exchangeCode) {
            return items;
        }

        @Override
        public List<InstrumentCatalogItem> findByExchangeAndSymbols(
                String exchangeCode,
                List<String> exchangeSymbols
        ) {
            return items.stream()
                    .filter(item -> item.exchangeCode().equals(exchangeCode)
                            && exchangeSymbols.contains(item.exchangeSymbol()))
                    .toList();
        }

        @Override
        public InstrumentCatalogUpsertStats upsertAll(
                List<InstrumentCatalogItem> values,
                Instant syncedAt
        ) {
            items = values.stream().map(value -> new InstrumentCatalogItem(
                    101L, value.exchangeCode(), value.instrumentType(), value.exchangeSymbol(),
                    value.internalSymbol(), value.baseAsset(), value.quoteAsset(), value.status(),
                    value.tickSize(), value.stepSize(), value.minQuantity(),
                    value.maxLimitQuantity(), value.maxMarketSize(), value.maxMarketSizeUnit(),
                    value.maxLimitNotionalUsd(), value.maxMarketNotionalUsd(), value.source(),
                    value.sourceSchemaVersion(), value.observedAt(), syncedAt,
                    value.nextRuleEffectiveAt(), value.ruleChecksum(), syncedAt, syncedAt)).toList();
            return new InstrumentCatalogUpsertStats(values.size(), 0);
        }

        @Override
        public InstrumentCatalogUpsertStats upsertVenueRuleFacts(
                List<InstrumentCatalogItem> values,
                Instant syncedAt
        ) {
            return upsertAll(values, syncedAt);
        }
    }
}
