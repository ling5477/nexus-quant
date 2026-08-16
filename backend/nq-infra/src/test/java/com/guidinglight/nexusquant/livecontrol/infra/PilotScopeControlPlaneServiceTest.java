package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeApprovalCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeAuthorityResolver;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GateY-6D trusted observation、canonicalization、authorization 与 forged replay 回归。 */
class PilotScopeControlPlaneServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T08:00:00Z");
    private static final AuthenticatedLiveControlActor ACTOR = new AuthenticatedLiveControlActor(11L);
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String C = "c".repeat(64);
    private static final String D = "d".repeat(64);
    private static final String E = "e".repeat(64);
    private static final String F = "f".repeat(64);
    private static final String ZERO = "0".repeat(64);

    private PilotScopeAuthorityResolver resolver;
    private PilotScopeFactTransactionService transactions;
    private LiveControlRepository liveRepository;
    private PilotScopeRepository scopeRepository;
    private LiveControlAuthorizationPort authorization;
    private DeterministicObservationAuthority observationAuthority;
    private PilotScopeControlPlaneService service;
    private RiskLimitSet risk;
    private PilotScopeAuthorityResolver.ResolvedScopeBindings bindings;

    @BeforeEach
    void setUp() {
        resolver = mock(PilotScopeAuthorityResolver.class);
        transactions = mock(PilotScopeFactTransactionService.class);
        liveRepository = mock(LiveControlRepository.class);
        scopeRepository = mock(PilotScopeRepository.class);
        authorization = mock(LiveControlAuthorizationPort.class);
        risk = risk();
        bindings = bindings();
        observationAuthority = new DeterministicObservationAuthority(ObservationVariant.VALID);
        service = service(observationAuthority);
        when(liveRepository.currentTime()).thenReturn(NOW);
        when(authorization.lockAndCheckRole(ACTOR.userId(), "OPERATOR")).thenReturn(true);
        when(resolver.resolve(any(), any()))
                .thenReturn(new PilotScopeAuthorityResolver.ResolvedAuthority(risk, bindings));
        when(transactions.materialize(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(4));
        when(transactions.approve(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void shouldMaterializeOnlyTrustedCanonicalObservationSet() {
        PilotScopeMaterializationCommand command = validCommand();

        var result = service.materialize(ACTOR, command);

        ArgumentCaptor<LiveSession> session = ArgumentCaptor.forClass(LiveSession.class);
        ArgumentCaptor<PilotScopeBinding> scope = ArgumentCaptor.forClass(PilotScopeBinding.class);
        ArgumentCaptor<PilotObservationSet> observations = ArgumentCaptor.forClass(PilotObservationSet.class);
        verify(transactions).materialize(any(), session.capture(), any(), any(), scope.capture(), observations.capture());
        assertTrue(scope.getValue().hasCanonicalHash(session.getValue()));
        assertEquals(command.expectedPilotScopeHash(), scope.getValue().pilotScopeHash());
        assertEquals(scope.getValue().id(), result.pilotScopeId());
        assertEquals(1, observationAuthority.calls());
        observations.getValue().observations().forEach(observation ->
                assertEquals(PilotObservationCanonicalEncoder.digest(observation), observation.observationPayloadHash()));
        assertEquals(scope.getValue().workerIdentity(),
                observations.getValue().balanceSnapshot().envelope().recorderIdentity());
    }

    @Test
    void productionUnavailableAuthorityShouldFailClosedBeforeTransaction() {
        service = service(new UnavailablePilotPrerequisiteObservationAuthority());

        LiveControlException failure = assertThrows(
                LiveControlException.class, () -> service.materialize(ACTOR, validCommand()));

        assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE", failure.code());
        verify(transactions, never()).materialize(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectExpectedScopeHashBeforeTrustedResolution() {
        PilotScopeMaterializationCommand command = withHash(validCommand(), F);

        LiveControlException failure = assertThrows(
                LiveControlException.class, () -> service.materialize(ACTOR, command));

        assertEquals("PILOT_SCOPE_HASH_MISMATCH", failure.code());
        assertEquals(0, observationAuthority.calls());
        verify(transactions, never()).materialize(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectRiskMismatchBeforeTrustedResolution() {
        PilotScopeMaterializationCommand value = validCommand();
        var mismatchedRisk = new PilotScopeMaterializationCommand.RiskSelection(
                value.risk().riskLimitSetId(), value.risk().riskLimitSetDigest(), value.risk().version(),
                value.risk().capitalCap(), new BigDecimal("99"), value.risk().maxSymbolPositionNotional(),
                value.risk().maxDailyRealizedLoss(), value.risk().maxDailyTotalLoss(), value.risk().maxOpenOrders(),
                value.risk().maxIntradayOrders(), value.risk().symbolAllowlist(),
                value.risk().maxSessionDurationSeconds(), value.risk().spreadLimitBps(),
                value.risk().slippageLimitBps(), value.risk().maxMarketDataAgeMs(),
                value.risk().minDataCoverageBps());

        LiveControlException failure = assertThrows(
                LiveControlException.class,
                () -> service.materialize(ACTOR, replaceRisk(value, mismatchedRisk)));

        assertEquals("PILOT_RISK_REFERENCE_MISMATCH", failure.code());
        assertEquals(0, observationAuthority.calls());
        verify(transactions, never()).materialize(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectSourceSchemaRecorderFutureStaleAndSymbolMismatch() {
        for (ObservationVariant variant : List.of(
                ObservationVariant.SOURCE_MISMATCH,
                ObservationVariant.SCHEMA_MISMATCH,
                ObservationVariant.RECORDER_MISMATCH,
                ObservationVariant.FUTURE_CLOCK,
                ObservationVariant.STALE_BALANCE,
                ObservationVariant.WRONG_SYMBOL)) {
            PilotScopeFactTransactionService isolatedTransactions = mock(PilotScopeFactTransactionService.class);
            PilotScopeControlPlaneService isolated = new PilotScopeControlPlaneService(
                    resolver, new DeterministicObservationAuthority(variant), isolatedTransactions,
                    liveRepository, scopeRepository, authorization);

            LiveControlException failure = assertThrows(
                    LiveControlException.class, () -> isolated.materialize(ACTOR, validCommand()), variant.name());

            assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_INVALID", failure.code(), variant.name());
            verify(isolatedTransactions, never()).materialize(any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    void postApprovalForgedRefreshShouldNotAppendAnotherObservationSet() {
        DeterministicObservationAuthority sequenced = new DeterministicObservationAuthority(
                ObservationVariant.VALID, ObservationVariant.FORGED_AFTER_APPROVAL);
        service = service(sequenced);
        PilotScopeMaterializationCommand command = validCommand();
        service.materialize(ACTOR, command);
        LiveSession storedSession = session(command);
        PilotScopeBinding storedScope = scope(command, storedSession, bindings);
        when(liveRepository.findSession(command.sessionId())).thenReturn(Optional.of(storedSession));
        when(scopeRepository.findBySessionId(command.sessionId())).thenReturn(Optional.of(storedScope));
        when(authorization.lockAndCheckRole(22L, OperatorApproval.REQUIRED_ROLE)).thenReturn(true);
        service.approve(new AuthenticatedLiveControlActor(22L), new PilotScopeApprovalCommand(
                UUID.randomUUID(), storedSession.id(), storedScope.id(), storedScope.pilotScopeHash(),
                "independent approval", NOW, NOW.plusSeconds(60)));

        LiveControlException replay = assertThrows(
                LiveControlException.class, () -> service.materialize(ACTOR, command));

        assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_INVALID", replay.code());
        assertEquals(2, sequenced.calls());
        verify(transactions, times(1)).materialize(any(), any(), any(), any(), any(), any());
        verify(transactions, times(1)).approve(any(), any());
    }

    @Test
    void shouldKeepPreflightActorBoundAndRoleProtected() {
        PilotScopeMaterializationCommand command = validCommand();
        LiveSession foreign = new LiveSession(
                session(command).id(), 99L, session(command).exchangeAccountId(), session(command).venue(),
                session(command).strategyReleaseId(), session(command).releaseDigest(),
                session(command).releaseAdmissionRevision(), session(command).riskLimitSetId(),
                session(command).riskLimitSetDigest(), session(command).credentialReference(),
                session(command).symbolAllowlist(), session(command).capitalCap(),
                session(command).executionWindowStart(), session(command).executionWindowEnd(),
                session(command).state(), session(command).version(), session(command).approvalScopeHash(),
                session(command).nextEventSequence(), session(command).createdBy(),
                session(command).createdAt(), session(command).updatedAt());
        when(liveRepository.findSession(foreign.id())).thenReturn(Optional.of(foreign));

        LiveControlException idor = assertThrows(
                LiveControlException.class, () -> service.preflight(ACTOR, foreign.id()));
        assertEquals("LIVE_SESSION_NOT_FOUND", idor.code());
        verify(transactions, never()).preflight(any(), any());

        when(authorization.lockAndCheckRole(ACTOR.userId(), "OPERATOR")).thenReturn(false);
        LiveControlException revoked = assertThrows(
                LiveControlException.class, () -> service.preflight(ACTOR, UUID.randomUUID()));
        assertEquals("PILOT_PREFLIGHT_OPERATOR_ROLE_REQUIRED", revoked.code());
    }

    @Test
    void shouldRejectApprovalForDifferentStoredScope() {
        PilotScopeMaterializationCommand value = validCommand();
        LiveSession session = session(value);
        PilotScopeBinding scope = scope(value, session, bindings);
        when(liveRepository.findSession(session.id())).thenReturn(Optional.of(session));
        when(scopeRepository.findBySessionId(session.id())).thenReturn(Optional.of(scope));
        when(authorization.lockAndCheckRole(22L, OperatorApproval.REQUIRED_ROLE)).thenReturn(true);
        PilotScopeApprovalCommand command = new PilotScopeApprovalCommand(
                UUID.randomUUID(), session.id(), UUID.randomUUID(), scope.pilotScopeHash(),
                "independent approval", NOW, NOW.plusSeconds(60));

        LiveControlException failure = assertThrows(
                LiveControlException.class,
                () -> service.approve(new AuthenticatedLiveControlActor(22), command));

        assertEquals("PILOT_APPROVAL_SCOPE_MISMATCH", failure.code());
        verify(transactions, never()).approve(any(), any());
    }

    private PilotScopeControlPlaneService service(PilotPrerequisiteObservationAuthority authority) {
        return new PilotScopeControlPlaneService(
                resolver, authority, transactions, liveRepository, scopeRepository, authorization);
    }

    private PilotScopeMaterializationCommand validCommand() {
        var riskSelection = new PilotScopeMaterializationCommand.RiskSelection(
                risk.id(), risk.canonicalDigest(), risk.version(), risk.capitalCap(), risk.maxOrderNotional(),
                risk.maxSymbolPositionNotional(), risk.maxDailyRealizedLoss(), risk.maxDailyTotalLoss(),
                risk.maxOpenOrders(), risk.maxIntradayOrders(), risk.symbolAllowlist(),
                risk.maxSessionDurationSeconds(), risk.spreadLimitBps(), risk.slippageLimitBps(),
                risk.maxMarketDataAgeMs(), risk.minDataCoverageBps());
        PilotScopeMaterializationCommand draft = new PilotScopeMaterializationCommand(
                UUID.randomUUID(), UUID.randomUUID(), 101, 202, "release-immutable-1", A, 7,
                riskSelection, List.of("BTC-USDT"), new BigDecimal("500"), NOW.plusSeconds(60),
                NOW.plusSeconds(3600), F, "idem-gatey6d-1", "request-1", "trace-1");
        LiveSession session = session(draft);
        return withHash(draft, scope(draft, session, bindings).pilotScopeHash());
    }

    private static LiveSession session(PilotScopeMaterializationCommand command) {
        return LiveSession.create(
                command.sessionId(), ACTOR.userId(), command.exchangeAccountId(), command.strategyReleaseId(),
                command.releaseDigest(), command.releaseAdmissionRevision(), command.risk().riskLimitSetId(),
                command.risk().riskLimitSetDigest(), command.credentialReference(), command.symbolAllowlist(),
                command.capitalCap(), command.executionWindowStart(), command.executionWindowEnd(), ACTOR.userId(), NOW);
    }

    private static PilotScopeBinding scope(
            PilotScopeMaterializationCommand command,
            LiveSession session,
            PilotScopeAuthorityResolver.ResolvedScopeBindings value
    ) {
        return new PilotScopeBinding(
                command.pilotScopeId(), session.id(), value.instrumentMetadataDigest(),
                value.instrumentSourceIdentity(), value.instrumentSourceSchemaVersion(), value.instrumentMaximumAgeMs(),
                value.feeScheduleDigest(), value.feeTier(), value.feeEvidenceClass(), value.feeSourceIdentity(),
                value.feeSourceSchemaVersion(), value.feeMaximumAgeMs(), value.balanceSourceIdentity(),
                value.balanceSourceSchemaVersion(), value.balanceMaximumAgeMs(), value.clockSourceIdentity(),
                value.clockSourceSchemaVersion(), value.clockMaximumAgeMs(), value.signedTimestampSource(),
                value.maximumToleratedSkewMs(), value.endpointPolicyVersion(), value.endpointPolicyDigest(),
                value.providerContractIdentity(), value.providerArtifactDigest(), value.workerIdentity(),
                value.workerReleaseDigest(), F, ACTOR.userId(), NOW).withCanonicalHash(session);
    }

    private static RiskLimitSet risk() {
        return new RiskLimitSet(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), 1,
                new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("500"),
                new BigDecimal("50"), new BigDecimal("100"), 3, 20, List.of("BTC-USDT"),
                7200, new BigDecimal("20"), new BigDecimal("30"), 1000, 9000, 11, NOW);
    }

    private static PilotScopeAuthorityResolver.ResolvedScopeBindings bindings() {
        List<PilotPrerequisiteObservation.InstrumentItem> items = List.of(item("BTC-USDT"));
        return new PilotScopeAuthorityResolver.ResolvedScopeBindings(
                PilotObservationCanonicalEncoder.instrumentMetadataDigest(items),
                "instrument-source@sha256:1", "instrument-source.v1", 60_000,
                B, "tier-1", PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE,
                "fee-source@sha256:1", "fee-source.v1", 60_000,
                "balance-source@sha256:1", "balance-source.v1", 5_000,
                "clock-source@sha256:1", "clock-source.v1", 5_000,
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, 100, "endpoint-policy.v1", B,
                "okx-spot-provider-contract.v1", C, "worker-release@sha256:1", D);
    }

    private static PilotPrerequisiteObservation.InstrumentItem item(String symbol) {
        return new PilotPrerequisiteObservation.InstrumentItem(
                symbol, PilotPrerequisiteObservation.TradingStatus.LIVE, new BigDecimal("0.1"),
                new BigDecimal("0.001"), new BigDecimal("0.001"),
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_PUBLISHED,
                new BigDecimal("5"), "USDT");
    }

    private static PilotScopeMaterializationCommand withHash(PilotScopeMaterializationCommand value, String hash) {
        return new PilotScopeMaterializationCommand(
                value.sessionId(), value.pilotScopeId(), value.exchangeAccountId(), value.credentialReference(),
                value.strategyReleaseId(), value.releaseDigest(), value.releaseAdmissionRevision(), value.risk(),
                value.symbolAllowlist(), value.capitalCap(), value.executionWindowStart(), value.executionWindowEnd(),
                hash, value.idempotencyKey(), value.requestId(), value.traceId());
    }

    private static PilotScopeMaterializationCommand replaceRisk(
            PilotScopeMaterializationCommand value,
            PilotScopeMaterializationCommand.RiskSelection riskSelection
    ) {
        return new PilotScopeMaterializationCommand(
                value.sessionId(), value.pilotScopeId(), value.exchangeAccountId(), value.credentialReference(),
                value.strategyReleaseId(), value.releaseDigest(), value.releaseAdmissionRevision(), riskSelection,
                value.symbolAllowlist(), value.capitalCap(), value.executionWindowStart(), value.executionWindowEnd(),
                value.expectedPilotScopeHash(), value.idempotencyKey(), value.requestId(), value.traceId());
    }

    private static PilotObservationSet observations(
            LiveSession session,
            PilotScopeBinding scope,
            Instant recordedAt,
            ObservationVariant variant
    ) {
        UUID setId = UUID.randomUUID();
        List<PilotPrerequisiteObservation.InstrumentItem> items = List.of(
                item(variant == ObservationVariant.WRONG_SYMBOL ? "ETH-USDT" : session.symbolAllowlist().getFirst()));
        String instrumentDigest = PilotObservationCanonicalEncoder.instrumentMetadataDigest(items);
        Instant balanceObservedAt = variant == ObservationVariant.STALE_BALANCE
                ? recordedAt.minusMillis(scope.balanceMaximumAgeMs() + 1) : recordedAt;
        Instant clockObservedAt = variant == ObservationVariant.FUTURE_CLOCK
                ? recordedAt.plusMillis(1) : recordedAt;
        String recorder = variant == ObservationVariant.RECORDER_MISMATCH ? "operator-forged" : scope.workerIdentity();
        String balanceSource = variant == ObservationVariant.SOURCE_MISMATCH
                || variant == ObservationVariant.FORGED_AFTER_APPROVAL
                ? "operator-forged" : scope.balanceSourceIdentity();
        String feeSchema = variant == ObservationVariant.SCHEMA_MISMATCH
                ? "operator-fee.v1" : scope.feeSourceSchemaVersion();
        BigDecimal balance = variant == ObservationVariant.FORGED_AFTER_APPROVAL
                ? new BigDecimal("999999") : new BigDecimal("1000");
        BigDecimal makerFee = variant == ObservationVariant.FORGED_AFTER_APPROVAL
                ? BigDecimal.ZERO : new BigDecimal("0.001");

        var instrument = instrument(
                setId, scope, recordedAt, recordedAt, recorder, instrumentDigest, items);
        var fee = fee(setId, scope, recordedAt, recordedAt, recorder, feeSchema, makerFee);
        var balanceSnapshot = balance(
                setId, scope, balanceObservedAt, recordedAt, recorder, balanceSource, balance);
        var clock = clock(setId, scope, clockObservedAt, recordedAt, recorder,
                variant == ObservationVariant.FORGED_AFTER_APPROVAL ? 0 : 10);
        return new PilotObservationSet(setId, scope.id(), instrument, fee, balanceSnapshot, clock);
    }

    private static PilotPrerequisiteObservation.InstrumentMetadata instrument(
            UUID setId,
            PilotScopeBinding scope,
            Instant observedAt,
            Instant recordedAt,
            String recorder,
            String digest,
            List<PilotPrerequisiteObservation.InstrumentItem> items
    ) {
        var draft = new PilotPrerequisiteObservation.InstrumentMetadata(
                envelope(UUID.randomUUID(), setId, scope, PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION,
                        "instrument-observation-" + UUID.randomUUID(), scope.instrumentSourceIdentity(),
                        scope.instrumentSourceSchemaVersion(), observedAt, recordedAt, recorder),
                digest, items);
        return new PilotPrerequisiteObservation.InstrumentMetadata(
                draft.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(draft)), digest, items);
    }

    private static PilotPrerequisiteObservation.FeeSchedule fee(
            UUID setId,
            PilotScopeBinding scope,
            Instant observedAt,
            Instant recordedAt,
            String recorder,
            String sourceSchema,
            BigDecimal makerFee
    ) {
        var draft = new PilotPrerequisiteObservation.FeeSchedule(
                envelope(UUID.randomUUID(), setId, scope, PilotPrerequisiteObservation.FeeSchedule.SCHEMA_VERSION,
                        "fee-observation-" + UUID.randomUUID(), scope.feeSourceIdentity(), sourceSchema,
                        observedAt, recordedAt, recorder),
                scope.feeScheduleDigest(), scope.feeTier(), scope.feeEvidenceClass(), makerFee,
                new BigDecimal("0.002"), PilotPrerequisiteObservation.FeeSchedule.LOSS_TREATMENT);
        return new PilotPrerequisiteObservation.FeeSchedule(
                draft.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(draft)),
                draft.feeScheduleDigest(), draft.feeTier(), draft.feeEvidenceClass(), draft.makerFeeRate(),
                draft.takerFeeRate(), draft.feeLossTreatment());
    }

    private static PilotPrerequisiteObservation.BalanceSnapshot balance(
            UUID setId,
            PilotScopeBinding scope,
            Instant observedAt,
            Instant recordedAt,
            String recorder,
            String sourceIdentity,
            BigDecimal availableBalance
    ) {
        var draft = new PilotPrerequisiteObservation.BalanceSnapshot(
                envelope(UUID.randomUUID(), setId, scope, PilotPrerequisiteObservation.BalanceSnapshot.SCHEMA_VERSION,
                        "balance-observation-" + UUID.randomUUID(), sourceIdentity,
                        scope.balanceSourceSchemaVersion(), observedAt, recordedAt, recorder),
                E, PilotPrerequisiteObservation.BalanceSnapshot.CURRENCY, availableBalance);
        return new PilotPrerequisiteObservation.BalanceSnapshot(
                draft.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(draft)),
                draft.balanceSnapshotDigest(), draft.balanceCurrency(), draft.availableBalance());
    }

    private static PilotPrerequisiteObservation.ClockSync clock(
            UUID setId,
            PilotScopeBinding scope,
            Instant observedAt,
            Instant recordedAt,
            String recorder,
            long observedSkewMs
    ) {
        var draft = new PilotPrerequisiteObservation.ClockSync(
                envelope(UUID.randomUUID(), setId, scope, PilotPrerequisiteObservation.ClockSync.SCHEMA_VERSION,
                        "clock-observation-" + UUID.randomUUID(), scope.clockSourceIdentity(),
                        scope.clockSourceSchemaVersion(), observedAt, recordedAt, recorder),
                D, scope.signedTimestampSource(), observedSkewMs);
        return new PilotPrerequisiteObservation.ClockSync(
                draft.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(draft)),
                draft.clockSyncObservationDigest(), draft.signedTimestampSource(), draft.observedSkewMs());
    }

    private static PilotPrerequisiteObservation.Envelope envelope(
            UUID observationId,
            UUID setId,
            PilotScopeBinding scope,
            String observationSchema,
            String observationIdentity,
            String sourceIdentity,
            String sourceSchema,
            Instant observedAt,
            Instant recordedAt,
            String recorder
    ) {
        return new PilotPrerequisiteObservation.Envelope(
                observationId, scope.id(), setId, observationSchema, observationIdentity, sourceIdentity,
                sourceSchema, observedAt, recordedAt, recorder, ZERO);
    }

    /** 仅 test scope 的 deterministic fake；不是 Spring bean，也不进入 production composition。 */
    private static final class DeterministicObservationAuthority
            implements PilotPrerequisiteObservationAuthority {

        private final List<ObservationVariant> variants;
        private final AtomicInteger calls = new AtomicInteger();

        private DeterministicObservationAuthority(ObservationVariant... variants) {
            this.variants = List.of(variants);
        }

        @Override
        public PilotObservationSet resolveTrustedObservationSet(
                LiveSession session,
                PilotScopeBinding scope,
                Instant resolvedAt
        ) {
            int call = calls.getAndIncrement();
            ObservationVariant variant = variants.get(Math.min(call, variants.size() - 1));
            return observations(session, scope, resolvedAt, variant);
        }

        private int calls() {
            return calls.get();
        }
    }

    private enum ObservationVariant {
        VALID,
        SOURCE_MISMATCH,
        SCHEMA_MISMATCH,
        RECORDER_MISMATCH,
        FUTURE_CLOCK,
        STALE_BALANCE,
        WRONG_SYMBOL,
        FORGED_AFTER_APPROVAL
    }
}
