package com.guidinglight.nexusquant.livecontrol.infra.okx;

import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPilotPrerequisiteRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPilotPrerequisiteSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * OKX production prerequisite observation capability；默认 runtime 不装配该 authority。
 *
 * <p>四类 facts 在一次 exact-credential JIT callback 内完整采集，任一失败都不会返回 partial set。</p>
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

    private final OkxPrivateCredentialExecutor credentialExecutor;

    public OkxPilotPrerequisiteObservationAuthority(OkxPrivateCredentialExecutor credentialExecutor) {
        this.credentialExecutor = Objects.requireNonNull(credentialExecutor, "credentialExecutor must not be null");
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
            OkxPilotPrerequisiteSnapshot snapshot = credentialExecutor.withActiveCredential(
                    session.ownerId(),
                    session.exchangeAccountId(),
                    session.credentialReference(),
                    JdbcOkxPrivateCredentialExecutor.OKX_API_V5,
                    credentialSession -> credentialSession.observePrerequisites(
                            new OkxPilotPrerequisiteRequest(session.symbolAllowlist()),
                            OkxPrivateEnvironment.PRODUCTION)
            );
            requireFreshSnapshot(snapshot, scope, resolvedAt);
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
    }

    private static PilotObservationSet materialize(
            LiveSession session,
            PilotScopeBinding scope,
            OkxPilotPrerequisiteSnapshot snapshot,
            Instant resolvedAt
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
        String instrumentDigest = PilotObservationCanonicalEncoder.instrumentMetadataDigest(instrumentItems);
        if (!constantTimeEquals(instrumentDigest, scope.instrumentMetadataDigest())) {
            throw new IllegalArgumentException("instrument digest mismatch");
        }

        OkxPilotPrerequisiteSnapshot.FeeFact firstFee = snapshot.fees().get(0);
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
        if (!exactFees || !firstFee.tierIdentity().equals(scope.feeTier())) {
            throw new IllegalArgumentException("fee collection scope mismatch");
        }
        String feeDigest = PilotObservationCanonicalEncoder.feeScheduleDigest(
                session.symbolAllowlist(), firstFee.tierIdentity(), firstFee.makerRate(), firstFee.takerRate());
        if (!constantTimeEquals(feeDigest, scope.feeScheduleDigest())) {
            throw new IllegalArgumentException("fee digest mismatch");
        }

        String balanceDigest = PilotObservationCanonicalEncoder.balanceSnapshotDigest(
                snapshot.availableUsdtBalance());
        String clockDigest = PilotObservationCanonicalEncoder.clockSyncDigest(
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, snapshot.observedSkewMs());
        String collectionKey = scope.id() + "|" + instrumentDigest + "|" + feeDigest + "|"
                + balanceDigest + "|" + clockDigest + "|" + resolvedAt;
        UUID observationSetId = deterministicUuid("set|" + collectionKey);

        PilotPrerequisiteObservation.InstrumentMetadata instrument = withHash(
                new PilotPrerequisiteObservation.InstrumentMetadata(
                        envelope("instrument", scope, observationSetId,
                                PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION,
                                INSTRUMENT_SOURCE, INSTRUMENT_SOURCE_SCHEMA, resolvedAt, collectionKey),
                        instrumentDigest,
                        instrumentItems
                ));
        PilotPrerequisiteObservation.FeeSchedule fee = withHash(
                new PilotPrerequisiteObservation.FeeSchedule(
                        envelope("fee", scope, observationSetId,
                                PilotPrerequisiteObservation.FeeSchedule.SCHEMA_VERSION,
                                FEE_SOURCE, FEE_SOURCE_SCHEMA, resolvedAt, collectionKey),
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
                                BALANCE_SOURCE, BALANCE_SOURCE_SCHEMA, resolvedAt, collectionKey),
                        balanceDigest,
                        PilotPrerequisiteObservation.BalanceSnapshot.CURRENCY,
                        snapshot.availableUsdtBalance()
                ));
        PilotPrerequisiteObservation.ClockSync clock = withHash(
                new PilotPrerequisiteObservation.ClockSync(
                        envelope("clock", scope, observationSetId,
                                PilotPrerequisiteObservation.ClockSync.SCHEMA_VERSION,
                                CLOCK_SOURCE, CLOCK_SOURCE_SCHEMA, resolvedAt, collectionKey),
                        clockDigest,
                        PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE,
                        snapshot.observedSkewMs()
                ));
        return new PilotObservationSet(observationSetId, scope.id(), instrument, fee, balance, clock);
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
            Instant resolvedAt,
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
                resolvedAt,
                resolvedAt,
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
