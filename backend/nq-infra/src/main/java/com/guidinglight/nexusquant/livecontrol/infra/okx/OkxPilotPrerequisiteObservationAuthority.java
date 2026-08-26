package com.guidinglight.nexusquant.livecontrol.infra.okx;

import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPilotPrerequisiteRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPilotPrerequisiteSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderOperation;
import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority.TrustedOperatorPilotBootstrap;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionAuthorityType;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;

import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OKX production prerequisite observation capability；默认 runtime 不装配该 authority。
 *
 * <p>五类 facts 在一次 exact-credential JIT callback 内完整采集，instrument metadata 随后经正式
 * catalog application service 做 bounded upsert；任一失败都不会返回 partial set。</p>
 */
public final class OkxPilotPrerequisiteObservationAuthority implements PilotPrerequisiteObservationAuthority {

    public static final String INSTRUMENT_SOURCE = "OKX_ACCOUNT_INSTRUMENTS";
    public static final String INSTRUMENT_SOURCE_SCHEMA = "okx-account-instruments.v5";
    public static final String FEE_SOURCE = "OKX_ACCOUNT_TRADE_FEE";
    public static final String FEE_SOURCE_SCHEMA = "okx-account-trade-fee.v5";
    public static final String BALANCE_SOURCE = "OKX_ACCOUNT_BALANCE";
    public static final String BALANCE_SOURCE_SCHEMA = "okx-account-balance.v5";
    public static final String CLOCK_SOURCE = "OKX_PUBLIC_TIME";
    public static final String CLOCK_SOURCE_SCHEMA = "okx-public-time.v5";
    public static final String MARKET_SOURCE = "OKX_MARKET_TICKER";
    public static final String MARKET_SOURCE_SCHEMA = "okx-market-ticker.v5";
    public static final long OPERATOR_INSTRUMENT_MAXIMUM_AGE_MS = 30_000L;
    public static final long OPERATOR_FEE_MAXIMUM_AGE_MS = 60_000L;
    public static final long OPERATOR_BALANCE_MAXIMUM_AGE_MS = 5_000L;
    public static final long OPERATOR_CLOCK_MAXIMUM_AGE_MS = 5_000L;
    public static final long OPERATOR_MAXIMUM_TOLERATED_SKEW_MS = 100L;
    public static final String OPERATOR_ENDPOINT_POLICY_VERSION =
            "okx-operator-pilot-exact-endpoints.v1";
    public static final String OPERATOR_PROVIDER_CONTRACT_IDENTITY =
            "okx-spot-provider-contract.v1";
    private static final String ZERO_DIGEST = "0".repeat(64);
    private static final String OPERATOR_ENDPOINT_POLICY_DIGEST = endpointPolicyDigest();

    private final OkxPrivateCredentialExecutor credentialExecutor;
    private final InstrumentCatalogService instrumentCatalogService;
    private final String releaseId;
    private final String releaseManifestSha256;

    public OkxPilotPrerequisiteObservationAuthority(
            OkxPrivateCredentialExecutor credentialExecutor,
            InstrumentCatalogService instrumentCatalogService,
            String releaseId,
            String releaseManifestSha256
    ) {
        this.credentialExecutor = Objects.requireNonNull(credentialExecutor, "credentialExecutor must not be null");
        this.instrumentCatalogService = Objects.requireNonNull(
                instrumentCatalogService, "instrumentCatalogService must not be null");
        if (releaseId == null || !releaseId.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("releaseId must be an exact commit");
        }
        if (releaseManifestSha256 == null || !releaseManifestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("releaseManifestSha256 must be lowercase SHA-256");
        }
        this.releaseId = releaseId;
        this.releaseManifestSha256 = releaseManifestSha256;
    }

    @Override
    public PilotObservationSet resolveTrustedObservationSet(
            LiveSession session,
            PilotScopeBinding scope,
            Instant resolvedAt
    ) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        try {
            requireExactScope(session, scope);
            OkxPilotPrerequisiteSnapshot snapshot = observeSnapshot(session);
            requireFreshSnapshot(snapshot, scope, resolvedAt);
            refreshCatalog(snapshot, resolvedAt);
            return materialize(session, scope, snapshot, resolvedAt);
        } catch (RuntimeException failure) {
            // transport/JDBC/parser cause 可能含 provider 细节；authority 边界只返回固定、脱敏分类。
            throw new LiveControlException(
                    "TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE",
                    "trusted prerequisite observation collection failed"
            );
        }
    }

    /**
     * {@code resolvedAt} 是受信 DB clock 在采集开始前生成的保守 observedAt。这里用采集末端的
     * local midpoint 和 OKX server time 证明整个 collection 没有越过任何 source freshness window，
     * 同时要求 trade-fee provider timestamp 本身仍然 fresh；不能用“刚记录”掩盖陈旧 venue facts。
     */
    private static void requireFreshSnapshot(
            OkxPilotPrerequisiteSnapshot snapshot,
            PilotScopeBinding scope,
            Instant resolvedAt
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        long calculatedSkew = Math.subtractExact(
                snapshot.okxServerTime().toEpochMilli(),
                snapshot.localClockMidpoint().toEpochMilli());
        if (calculatedSkew != snapshot.observedSkewMs()) {
            throw new IllegalArgumentException("clock observation is internally inconsistent");
        }
        if (calculatedSkew < -scope.maximumToleratedSkewMs()
                || calculatedSkew > scope.maximumToleratedSkewMs()) {
            throw new IllegalArgumentException("clock observation exceeds the exact scope");
        }

        long collectionMaximumAgeMs = Math.min(
                Math.min(scope.instrumentMaximumAgeMs(), scope.feeMaximumAgeMs()),
                Math.min(scope.balanceMaximumAgeMs(), scope.clockMaximumAgeMs()));
        if (snapshot.localClockMidpoint().isBefore(resolvedAt)
                || resolvedAt.plusMillis(collectionMaximumAgeMs).isBefore(snapshot.localClockMidpoint())) {
            throw new IllegalArgumentException("prerequisite collection is stale");
        }

        Instant earliestFreshFeeTimestamp = snapshot.okxServerTime().minusMillis(scope.feeMaximumAgeMs());
        if (snapshot.fees().stream().map(OkxPilotPrerequisiteSnapshot.FeeFact::providerTimestamp)
                .anyMatch(value -> value.isBefore(earliestFreshFeeTimestamp)
                        || value.isAfter(snapshot.okxServerTime()))) {
            throw new IllegalArgumentException("fee observation is stale");
        }
        Instant earliestFreshMarketTimestamp = snapshot.okxServerTime()
                .minusMillis(scope.instrumentMaximumAgeMs());
        for (int index = 0; index < snapshot.markets().size(); index++) {
            OkxPilotPrerequisiteSnapshot.MarketFact market = snapshot.markets().get(index);
            if (!market.instrument().equals(snapshot.instruments().get(index).instrument())
                    || market.observedAt().isBefore(earliestFreshMarketTimestamp)
                    || market.observedAt().isAfter(snapshot.okxServerTime())) {
                throw new IllegalArgumentException("market observation is stale or outside instrument scope");
            }
        }
    }

    private OkxPilotPrerequisiteSnapshot observeSnapshot(LiveSession session) {
        return credentialExecutor.withActiveCredential(
                session.ownerId(),
                session.exchangeAccountId(),
                session.credentialReference(),
                JdbcOkxPrivateCredentialExecutor.OKX_API_V5,
                credentialSession -> credentialSession.observePrerequisites(
                        new OkxPilotPrerequisiteRequest(session.symbolAllowlist()),
                        OkxPrivateEnvironment.PRODUCTION)
        );
    }

    @Override
    public TrustedOperatorPilotBootstrap bootstrapTrustedOperatorPilotScope(
            LiveSession session,
            UUID pilotScopeId,
            long createdBy,
            Instant resolvedAt
    ) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        String stage = "OBSERVATION";
        try {
            if (session.authorityType() != LiveSessionAuthorityType.OPERATOR_PILOT
                    || createdBy <= 0 || createdBy != session.ownerId()) {
                throw new IllegalArgumentException("operator pilot bootstrap scope mismatch");
            }
            OkxPilotPrerequisiteSnapshot snapshot = observeSnapshot(session);
            stage = "SCOPE";
            PilotScopeBinding scope = bootstrapScope(
                    session, pilotScopeId, createdBy, snapshot, resolvedAt);
            stage = "FRESHNESS";
            requireFreshSnapshot(snapshot, scope, resolvedAt);
            stage = "CATALOG";
            refreshCatalog(snapshot, resolvedAt);
            stage = "MATERIALIZATION";
            PilotObservationSet observations = materialize(session, scope, snapshot, resolvedAt);
            return new TrustedOperatorPilotBootstrap(scope, observations);
        } catch (RuntimeException failure) {
            throw new LiveControlException(
                    "TRUSTED_OPERATOR_PILOT_SCOPE_BOOTSTRAP_" + stage + "_FAILED",
                    "trusted operator pilot scope bootstrap failed"
            );
        }
    }

    private void refreshCatalog(OkxPilotPrerequisiteSnapshot snapshot, Instant syncedAt) {
        List<InstrumentCatalogItem> items = snapshot.instruments().stream()
                .map(value -> catalogItem(value, syncedAt))
                .toList();
        instrumentCatalogService.upsertCatalogItems(items, syncedAt);
        List<InstrumentCatalogItem> stored = instrumentCatalogService.findByExchangeAndSymbols(
                "OKX", items.stream().map(InstrumentCatalogItem::exchangeSymbol).toList());
        if (stored.size() != items.size()) {
            throw new IllegalStateException("instrument catalog refresh did not persist the exact scope");
        }
        for (int index = 0; index < items.size(); index++) {
            InstrumentCatalogItem expected = items.get(index);
            InstrumentCatalogItem actual = stored.get(index);
            if (!expected.exchangeSymbol().equals(actual.exchangeSymbol())
                    || !expected.status().equals(actual.status())
                    || expected.tickSize().compareTo(actual.tickSize()) != 0
                    || expected.stepSize().compareTo(actual.stepSize()) != 0
                    || expected.minQuantity().compareTo(actual.minQuantity()) != 0) {
                throw new IllegalStateException("instrument catalog refresh readback mismatch");
            }
        }
    }

    private static InstrumentCatalogItem catalogItem(
            OkxPilotPrerequisiteSnapshot.InstrumentFact value,
            Instant syncedAt
    ) {
        String[] assets = value.instrument().split("-", -1);
        if (assets.length != 2 || !"USDT".equals(assets[1])) {
            throw new IllegalArgumentException("unsupported OKX Spot instrument");
        }
        return new InstrumentCatalogItem(
                null, "OKX", "SPOT", value.instrument(), value.instrument(),
                assets[0], assets[1], tradingStatus(value.state()).name(),
                value.tickSize(), value.lotSize(), value.minimumOrderSize(),
                null, null, null, null, null,
                INSTRUMENT_SOURCE, null, null, syncedAt, null, null,
                null, null);
    }

    private PilotScopeBinding bootstrapScope(
            LiveSession session,
            UUID pilotScopeId,
            long createdBy,
            OkxPilotPrerequisiteSnapshot snapshot,
            Instant resolvedAt
    ) {
        ConstraintFacts facts = constraintFacts(session, snapshot);
        PilotScopeBinding draft = new PilotScopeBinding(
                pilotScopeId,
                session.id(),
                facts.instrumentDigest(),
                INSTRUMENT_SOURCE,
                INSTRUMENT_SOURCE_SCHEMA,
                OPERATOR_INSTRUMENT_MAXIMUM_AGE_MS,
                facts.feeDigest(),
                facts.firstFee().tierIdentity(),
                PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE,
                FEE_SOURCE,
                FEE_SOURCE_SCHEMA,
                OPERATOR_FEE_MAXIMUM_AGE_MS,
                BALANCE_SOURCE,
                BALANCE_SOURCE_SCHEMA,
                OPERATOR_BALANCE_MAXIMUM_AGE_MS,
                CLOCK_SOURCE,
                CLOCK_SOURCE_SCHEMA,
                OPERATOR_CLOCK_MAXIMUM_AGE_MS,
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE,
                OPERATOR_MAXIMUM_TOLERATED_SKEW_MS,
                OPERATOR_ENDPOINT_POLICY_VERSION,
                OPERATOR_ENDPOINT_POLICY_DIGEST,
                OPERATOR_PROVIDER_CONTRACT_IDENTITY,
                releaseManifestSha256,
                "gatey-minimal-live-pilot@" + releaseId,
                releaseManifestSha256,
                ZERO_DIGEST,
                createdBy,
                resolvedAt
        );
        return draft.withCanonicalHash(session);
    }

    private static PilotObservationSet materialize(
            LiveSession session,
            PilotScopeBinding scope,
            OkxPilotPrerequisiteSnapshot snapshot,
            Instant resolvedAt
    ) {
        ConstraintFacts facts = constraintFacts(session, snapshot);
        List<PilotPrerequisiteObservation.InstrumentItem> instrumentItems = facts.instrumentItems();
        String instrumentDigest = facts.instrumentDigest();
        if (!constantTimeEquals(instrumentDigest, scope.instrumentMetadataDigest())) {
            throw new IllegalArgumentException("instrument digest mismatch");
        }
        OkxPilotPrerequisiteSnapshot.FeeFact firstFee = facts.firstFee();
        if (!firstFee.tierIdentity().equals(scope.feeTier())) {
            throw new IllegalArgumentException("fee collection scope mismatch");
        }
        String feeDigest = facts.feeDigest();
        if (!constantTimeEquals(feeDigest, scope.feeScheduleDigest())) {
            throw new IllegalArgumentException("fee digest mismatch");
        }

        java.math.BigDecimal availableBalance = snapshot.availableUsdtBalance()
                .setScale(8, RoundingMode.DOWN);
        String balanceDigest = PilotObservationCanonicalEncoder.balanceSnapshotDigest(availableBalance);
        String clockDigest = PilotObservationCanonicalEncoder.clockSyncDigest(
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, snapshot.observedSkewMs());
        Instant recordedAt = snapshot.localClockMidpoint().truncatedTo(ChronoUnit.MICROS);
        String collectionKey = scope.id() + "|" + instrumentDigest + "|" + feeDigest + "|"
                + balanceDigest + "|" + clockDigest + "|" + resolvedAt;
        UUID observationSetId = deterministicUuid("set|" + collectionKey);

        PilotPrerequisiteObservation.InstrumentMetadata instrument = withHash(
                new PilotPrerequisiteObservation.InstrumentMetadata(
                        envelope("instrument", scope, observationSetId,
                                PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION,
                                INSTRUMENT_SOURCE, INSTRUMENT_SOURCE_SCHEMA,
                                resolvedAt, recordedAt, collectionKey),
                        instrumentDigest,
                        instrumentItems
                ));
        PilotPrerequisiteObservation.FeeSchedule fee = withHash(
                new PilotPrerequisiteObservation.FeeSchedule(
                        envelope("fee", scope, observationSetId,
                                PilotPrerequisiteObservation.FeeSchedule.SCHEMA_VERSION,
                                FEE_SOURCE, FEE_SOURCE_SCHEMA,
                                resolvedAt, recordedAt, collectionKey),
                        feeDigest,
                        firstFee.tierIdentity(),
                        PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE,
                        firstFee.makerRate(),
                        firstFee.takerRate(),
                        PilotPrerequisiteObservation.FeeSchedule.LOSS_TREATMENT
                ));
        PilotPrerequisiteObservation.BalanceSnapshot balance = withHash(
                new PilotPrerequisiteObservation.BalanceSnapshot(
                        envelope("balance", scope, observationSetId,
                                PilotPrerequisiteObservation.BalanceSnapshot.SCHEMA_VERSION,
                                BALANCE_SOURCE, BALANCE_SOURCE_SCHEMA,
                                resolvedAt, recordedAt, collectionKey),
                        balanceDigest,
                        PilotPrerequisiteObservation.BalanceSnapshot.CURRENCY,
                        availableBalance
                ));
        PilotPrerequisiteObservation.ClockSync clock = withHash(
                new PilotPrerequisiteObservation.ClockSync(
                        envelope("clock", scope, observationSetId,
                                PilotPrerequisiteObservation.ClockSync.SCHEMA_VERSION,
                                CLOCK_SOURCE, CLOCK_SOURCE_SCHEMA,
                                resolvedAt, recordedAt, collectionKey),
                        clockDigest,
                        PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE,
                        snapshot.observedSkewMs()
                ));
        OkxPilotPrerequisiteSnapshot.MarketFact marketFact = snapshot.markets().getFirst();
        String marketDigest = PilotObservationCanonicalEncoder.marketSnapshotDigest(
                marketFact.instrument(), marketFact.bestAsk(), marketFact.observedAt(),
                MARKET_SOURCE, MARKET_SOURCE_SCHEMA);
        PilotPrerequisiteObservation.MarketSnapshot marketDraft =
                new PilotPrerequisiteObservation.MarketSnapshot(
                        new PilotPrerequisiteObservation.Envelope(
                                deterministicUuid("market|" + collectionKey),
                                scope.id(), observationSetId,
                                PilotPrerequisiteObservation.MarketSnapshot.SCHEMA_VERSION,
                                "okx:market:" + marketDigest.substring(0, 32),
                                MARKET_SOURCE, MARKET_SOURCE_SCHEMA,
                                marketFact.observedAt(), recordedAt,
                                scope.workerIdentity(), "0".repeat(64)),
                        marketDigest, marketFact.instrument(), marketFact.bestAsk());
        PilotPrerequisiteObservation.MarketSnapshot market =
                new PilotPrerequisiteObservation.MarketSnapshot(
                        marketDraft.envelope().withPayloadHash(
                                PilotObservationCanonicalEncoder.digest(marketDraft)),
                        marketDigest, marketFact.instrument(), marketFact.bestAsk());
        return new PilotObservationSet(
                observationSetId, scope.id(), instrument, fee, balance, clock, market);
    }

    private static ConstraintFacts constraintFacts(
            LiveSession session,
            OkxPilotPrerequisiteSnapshot snapshot
    ) {
        List<PilotPrerequisiteObservation.InstrumentItem> instrumentItems = snapshot.instruments().stream()
                .map(value -> new PilotPrerequisiteObservation.InstrumentItem(
                        value.instrument(),
                        tradingStatus(value.state()),
                        value.tickSize(),
                        value.lotSize(),
                        value.minimumOrderSize(),
                        PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_NOT_PUBLISHED,
                        null,
                        null
                ))
                .toList();
        if (!instrumentItems.stream().map(PilotPrerequisiteObservation.InstrumentItem::symbol).toList()
                .equals(session.symbolAllowlist())) {
            throw new IllegalArgumentException("instrument collection scope mismatch");
        }
        OkxPilotPrerequisiteSnapshot.FeeFact firstFee = snapshot.fees().getFirst();
        boolean exactFees = true;
        for (int index = 0; index < snapshot.fees().size(); index++) {
            OkxPilotPrerequisiteSnapshot.FeeFact fee = snapshot.fees().get(index);
            OkxPilotPrerequisiteSnapshot.InstrumentFact instrument = snapshot.instruments().get(index);
            exactFees &= fee.instrument().equals(instrument.instrument())
                    && fee.groupId().equals(instrument.feeGroupId())
                    && fee.tierIdentity().equals(firstFee.tierIdentity())
                    && fee.makerRate().compareTo(firstFee.makerRate()) == 0
                    && fee.takerRate().compareTo(firstFee.takerRate()) == 0;
        }
        if (!exactFees) {
            throw new IllegalArgumentException("fee collection scope mismatch");
        }
        String instrumentDigest = PilotObservationCanonicalEncoder.instrumentMetadataDigest(instrumentItems);
        String feeDigest = PilotObservationCanonicalEncoder.feeScheduleDigest(
                session.symbolAllowlist(), firstFee.tierIdentity(), firstFee.makerRate(), firstFee.takerRate());
        return new ConstraintFacts(instrumentItems, instrumentDigest, firstFee, feeDigest);
    }

    private record ConstraintFacts(
            List<PilotPrerequisiteObservation.InstrumentItem> instrumentItems,
            String instrumentDigest,
            OkxPilotPrerequisiteSnapshot.FeeFact firstFee,
            String feeDigest
    ) {
    }

    private static void requireExactScope(LiveSession session, PilotScopeBinding scope) {
        boolean exact = session.id().equals(scope.sessionId())
                && INSTRUMENT_SOURCE.equals(scope.instrumentSourceIdentity())
                && INSTRUMENT_SOURCE_SCHEMA.equals(scope.instrumentSourceSchemaVersion())
                && FEE_SOURCE.equals(scope.feeSourceIdentity())
                && FEE_SOURCE_SCHEMA.equals(scope.feeSourceSchemaVersion())
                && BALANCE_SOURCE.equals(scope.balanceSourceIdentity())
                && BALANCE_SOURCE_SCHEMA.equals(scope.balanceSourceSchemaVersion())
                && CLOCK_SOURCE.equals(scope.clockSourceIdentity())
                && CLOCK_SOURCE_SCHEMA.equals(scope.clockSourceSchemaVersion())
                && scope.feeEvidenceClass() == PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE
                && PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE.equals(scope.signedTimestampSource());
        if (!exact) {
            throw new IllegalArgumentException("trusted source scope mismatch");
        }
    }

    private static PilotPrerequisiteObservation.Envelope envelope(
            String type,
            PilotScopeBinding scope,
            UUID observationSetId,
            String schemaVersion,
            String sourceIdentity,
            String sourceSchemaVersion,
            Instant observedAt,
            Instant recordedAt,
            String collectionKey
    ) {
        String identityHash = sha256(type + "|" + collectionKey);
        return new PilotPrerequisiteObservation.Envelope(
                deterministicUuid(type + "|" + collectionKey),
                scope.id(),
                observationSetId,
                schemaVersion,
                "okx:" + type + ":" + identityHash.substring(0, 32),
                sourceIdentity,
                sourceSchemaVersion,
                observedAt,
                recordedAt,
                scope.workerIdentity(),
                "0".repeat(64)
        );
    }

    private static PilotPrerequisiteObservation.InstrumentMetadata withHash(
            PilotPrerequisiteObservation.InstrumentMetadata value
    ) {
        String hash = PilotObservationCanonicalEncoder.digest(value);
        return new PilotPrerequisiteObservation.InstrumentMetadata(
                value.envelope().withPayloadHash(hash), value.instrumentMetadataDigest(), value.items());
    }

    private static PilotPrerequisiteObservation.FeeSchedule withHash(
            PilotPrerequisiteObservation.FeeSchedule value
    ) {
        String hash = PilotObservationCanonicalEncoder.digest(value);
        return new PilotPrerequisiteObservation.FeeSchedule(
                value.envelope().withPayloadHash(hash), value.feeScheduleDigest(), value.feeTier(),
                value.feeEvidenceClass(), value.makerFeeRate(), value.takerFeeRate(), value.feeLossTreatment());
    }

    private static PilotPrerequisiteObservation.BalanceSnapshot withHash(
            PilotPrerequisiteObservation.BalanceSnapshot value
    ) {
        String hash = PilotObservationCanonicalEncoder.digest(value);
        return new PilotPrerequisiteObservation.BalanceSnapshot(
                value.envelope().withPayloadHash(hash), value.balanceSnapshotDigest(),
                value.balanceCurrency(), value.availableBalance());
    }

    private static PilotPrerequisiteObservation.ClockSync withHash(
            PilotPrerequisiteObservation.ClockSync value
    ) {
        String hash = PilotObservationCanonicalEncoder.digest(value);
        return new PilotPrerequisiteObservation.ClockSync(
                value.envelope().withPayloadHash(hash), value.clockSyncObservationDigest(),
                value.signedTimestampSource(), value.observedSkewMs());
    }

    private static PilotPrerequisiteObservation.TradingStatus tradingStatus(String state) {
        return switch (state) {
            case "live" -> PilotPrerequisiteObservation.TradingStatus.LIVE;
            case "suspend" -> PilotPrerequisiteObservation.TradingStatus.SUSPEND;
            case "preopen" -> PilotPrerequisiteObservation.TradingStatus.PREOPEN;
            case "test" -> PilotPrerequisiteObservation.TradingStatus.TEST;
            default -> throw new IllegalArgumentException("unsupported OKX instrument state");
        };
    }

    private static UUID deterministicUuid(String value) {
        byte[] digest = sha256Bytes(value);
        ByteBuffer bytes = ByteBuffer.wrap(digest);
        long high = bytes.getLong();
        long low = bytes.getLong();
        high = (high & 0xffffffffffff0fffL) | 0x0000000000005000L;
        low = (low & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(high, low);
    }

    private static String endpointPolicyDigest() {
        List<String> prerequisiteEndpoints = List.of(
                "GET /api/v5/account/balance",
                "GET /api/v5/account/instruments",
                "GET /api/v5/account/trade-fee",
                "GET /api/v5/market/ticker",
                "GET /api/v5/public/time"
        );
        String canonical = java.util.stream.Stream.concat(
                        prerequisiteEndpoints.stream(),
                        OkxSpotProviderOperation.exactAllowlist().stream()
                                .map(operation -> operation.method() + " " + operation.path()))
                .distinct()
                .sorted()
                .collect(Collectors.joining("\n"));
        return sha256(OPERATOR_ENDPOINT_POLICY_VERSION + "\n" + canonical);
    }

    private static String sha256(String value) {
        return java.util.HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
