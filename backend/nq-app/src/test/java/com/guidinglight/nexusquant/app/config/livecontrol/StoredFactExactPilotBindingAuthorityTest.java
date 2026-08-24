package com.guidinglight.nexusquant.app.config.livecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionState;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionStateRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StoredFactExactPilotBindingAuthorityTest {

    private static final Instant NOW = Instant.parse("2026-08-22T01:00:00Z");
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String DIGEST_C = "c".repeat(64);
    private static final long OWNER = 11L;
    private static final long ACCOUNT = 21L;
    private static final long CREDENTIAL = 31L;

    @Test
    void resolvesOnlyStoredSummaryFactsWithoutCredentialMaterialOrProviderIo() {
        LiveControlRepository liveRepository = mock(LiveControlRepository.class);
        PilotScopeRepository scopeRepository = mock(PilotScopeRepository.class);
        ExchangeAccountRepository accountRepository = mock(ExchangeAccountRepository.class);
        ExchangeAccountCredentialRepository credentialRepository = mock(ExchangeAccountCredentialRepository.class);
        StrategyReleaseAdmissionStateRepository admissionRepository =
                mock(StrategyReleaseAdmissionStateRepository.class);
        InstrumentCatalogReadPort instrumentCatalog = mock(InstrumentCatalogReadPort.class);
        KillSwitchService killSwitchService = mock(KillSwitchService.class);

        RiskLimitSet risk = risk();
        LiveSession session = session(risk);
        PilotScopeBinding scope = scope(session);
        PilotObservationSet observations = observations(scope);
        ExactPilotBinding.OrderEnvelope order = order();
        ExactPilotBindingCommand command = new ExactPilotBindingCommand(
                UUID.randomUUID(), session.id(), scope.id(), observations.id(), order,
                session.executionWindowStart(), session.executionWindowEnd(),
                new ExactPilotBinding.Correlation("request-1", "trace-1", "idempotency-1"),
                NOW.plusSeconds(300));

        when(liveRepository.findSession(session.id())).thenReturn(Optional.of(session));
        when(liveRepository.lockAndValidateSessionReferences(session)).thenReturn(true);
        when(liveRepository.findRiskLimitSet(risk.id())).thenReturn(Optional.of(risk));
        when(scopeRepository.findBySessionId(session.id())).thenReturn(Optional.of(scope));
        when(scopeRepository.findObservationSet(scope.id(), observations.id()))
                .thenReturn(Optional.of(observations));
        when(scopeRepository.findLatestCompleteObservationSet(scope.id()))
                .thenReturn(Optional.of(observations));
        when(scopeRepository.findValidPilotApproval(scope, NOW))
                .thenReturn(Optional.of(mock(OperatorApproval.class)));
        when(accountRepository.findByIdForOwner(OWNER, ACCOUNT)).thenReturn(Optional.of(
                new ExchangeAccountSummary(ACCOUNT, 121L, OWNER, "OKX", "LIVE", "pilot", null,
                        false, "ACTIVE")));
        ExchangeAccountCredentialSummary credential = credential();
        when(credentialRepository.findByCredentialIdForOwner(OWNER, ACCOUNT, CREDENTIAL))
                .thenReturn(Optional.of(credential));
        when(admissionRepository.loadByPublishRecordId(session.strategyReleaseId())).thenReturn(
                new StrategyReleaseAdmissionState(
                        session.strategyReleaseId(), session.releaseAdmissionRevision(), 1,
                        session.releaseDigest(), DIGEST_B, "strategy-release-manifest.v1",
                        NOW.minusSeconds(600), NOW.minusSeconds(700), NOW.minusSeconds(600)));
        when(instrumentCatalog.findByExchangeAndSymbols("OKX", List.of("BTC-USDT")))
                .thenReturn(List.of(instrument()));
        when(killSwitchService.snapshot()).thenReturn(new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING, KillSwitchStatus.ENGAGED, 1, "PILOT_LOCKED",
                "DURABLE_STORE", NOW.minusSeconds(600), NOW, "kill-trace"));

        ExactPilotRuntimeIdentity runtimeIdentity = ExactPilotRuntimeIdentity.from(
                new ReadOnlyProviderObservationRuntimeIdentity(
                        "1".repeat(40), "1".repeat(40),
                        ReadOnlyProviderObservationRuntimeIdentity.CAPABILITY, "127.0.0.1", 21),
                DIGEST_C, "server-a", ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE);
        StoredFactExactPilotBindingAuthority authority = new StoredFactExactPilotBindingAuthority(
                liveRepository, scopeRepository, accountRepository, credentialRepository,
                admissionRepository, instrumentCatalog, killSwitchService, runtimeIdentity);

        ExactPilotBinding.AuthoritativeFacts resolved = authority.resolveForCreation(
                new AuthenticatedLiveControlActor(OWNER), command, NOW);

        assertEquals(session.id(), resolved.sessionId());
        assertEquals(scope.id(), resolved.pilotScopeId());
        assertEquals(observations.id(), resolved.observationSetId());
        assertEquals(order, resolved.order());
        assertEquals(CREDENTIAL, resolved.account().credentialReferenceId());
        assertEquals(KillSwitchStatus.ENGAGED.name(), resolved.riskPolicy().killSwitchState());
        verify(credentialRepository).findByCredentialIdForOwner(OWNER, ACCOUNT, CREDENTIAL);
        verifyNoMoreInteractions(credentialRepository);
    }

    private static RiskLimitSet risk() {
        return new RiskLimitSet(
                UUID.randomUUID(), 1, decimal("25"), decimal("20"), decimal("25"),
                decimal("5"), decimal("10"), 1, 2, List.of("BTC-USDT"), 900,
                decimal("10"), decimal("10"), 1_000, 9_000, OWNER, NOW.minusSeconds(900));
    }

    private static LiveSession session(RiskLimitSet risk) {
        return LiveSession.create(
                UUID.randomUUID(), OWNER, ACCOUNT, "release-record", DIGEST_A, 7, risk.id(),
                risk.canonicalDigest(), CREDENTIAL, List.of("BTC-USDT"), decimal("25"),
                NOW.minusSeconds(60), NOW.plusSeconds(600), OWNER, NOW.minusSeconds(120));
    }

    private static PilotScopeBinding scope(LiveSession session) {
        PilotPrerequisiteObservation.InstrumentItem item = observedItem();
        PilotScopeBinding draft = new PilotScopeBinding(
                UUID.randomUUID(), session.id(), PilotObservationCanonicalEncoder.instrumentMetadataDigest(List.of(item)),
                "instrument-source", "instrument-source.v2", 60_000, DIGEST_A, "tier-1",
                PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE, "fee-source", "fee-source.v1", 60_000,
                "balance-source", "balance-source.v1", 5_000, "clock-source", "clock-source.v1", 5_000,
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, 100, "endpoint-policy.v1", DIGEST_A,
                "okx-spot-provider.v1", DIGEST_B, "worker-a", DIGEST_C, DIGEST_A, OWNER,
                NOW.minusSeconds(120));
        return draft.withCanonicalHash(session);
    }

    private static PilotObservationSet observations(PilotScopeBinding scope) {
        UUID setId = UUID.randomUUID();
        PilotPrerequisiteObservation.InstrumentItem item = observedItem();
        var instrumentDraft = new PilotPrerequisiteObservation.InstrumentMetadata(
                envelope(scope, setId, PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION,
                        "instrument-observation", scope.instrumentSourceIdentity(),
                        scope.instrumentSourceSchemaVersion()),
                PilotObservationCanonicalEncoder.instrumentMetadataDigest(List.of(item)), List.of(item));
        var instrument = new PilotPrerequisiteObservation.InstrumentMetadata(
                instrumentDraft.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(instrumentDraft)),
                instrumentDraft.instrumentMetadataDigest(), instrumentDraft.items());
        var feeDraft = new PilotPrerequisiteObservation.FeeSchedule(
                envelope(scope, setId, PilotPrerequisiteObservation.FeeSchedule.SCHEMA_VERSION,
                        "fee-observation", scope.feeSourceIdentity(), scope.feeSourceSchemaVersion()),
                scope.feeScheduleDigest(), scope.feeTier(), scope.feeEvidenceClass(),
                new BigDecimal("0.001"), new BigDecimal("0.002"),
                PilotPrerequisiteObservation.FeeSchedule.LOSS_TREATMENT);
        var fee = new PilotPrerequisiteObservation.FeeSchedule(
                feeDraft.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(feeDraft)),
                feeDraft.feeScheduleDigest(), feeDraft.feeTier(), feeDraft.feeEvidenceClass(),
                feeDraft.makerFeeRate(), feeDraft.takerFeeRate(), feeDraft.feeLossTreatment());
        var balanceDraft = new PilotPrerequisiteObservation.BalanceSnapshot(
                envelope(scope, setId, PilotPrerequisiteObservation.BalanceSnapshot.SCHEMA_VERSION,
                        "balance-observation", scope.balanceSourceIdentity(), scope.balanceSourceSchemaVersion()),
                DIGEST_B, "USDT", decimal("100"));
        var balance = new PilotPrerequisiteObservation.BalanceSnapshot(
                balanceDraft.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(balanceDraft)),
                balanceDraft.balanceSnapshotDigest(), balanceDraft.balanceCurrency(), balanceDraft.availableBalance());
        var clockDraft = new PilotPrerequisiteObservation.ClockSync(
                envelope(scope, setId, PilotPrerequisiteObservation.ClockSync.SCHEMA_VERSION,
                        "clock-observation", scope.clockSourceIdentity(), scope.clockSourceSchemaVersion()),
                DIGEST_C, PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, 10);
        var clock = new PilotPrerequisiteObservation.ClockSync(
                clockDraft.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(clockDraft)),
                clockDraft.clockSyncObservationDigest(), clockDraft.signedTimestampSource(),
                clockDraft.observedSkewMs());
        return new PilotObservationSet(setId, scope.id(), instrument, fee, balance, clock);
    }

    private static PilotPrerequisiteObservation.Envelope envelope(
            PilotScopeBinding scope,
            UUID setId,
            String schema,
            String identity,
            String source,
            String sourceSchema
    ) {
        return new PilotPrerequisiteObservation.Envelope(
                UUID.randomUUID(), scope.id(), setId, schema, identity, source, sourceSchema,
                NOW, NOW, scope.workerIdentity(), "0".repeat(64));
    }

    private static PilotPrerequisiteObservation.InstrumentItem observedItem() {
        return new PilotPrerequisiteObservation.InstrumentItem(
                "BTC-USDT", PilotPrerequisiteObservation.TradingStatus.LIVE,
                new BigDecimal("0.1"), new BigDecimal("0.001"), new BigDecimal("0.001"),
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_PUBLISHED,
                new BigDecimal("5"), "USDT");
    }

    private static ExactPilotBinding.OrderEnvelope order() {
        return new ExactPilotBinding.OrderEnvelope(
                101L, "BTC-USDT", ExactPilotBinding.Side.BUY, ExactPilotBinding.OrderType.LIMIT,
                decimal("100"), decimal("0.1"), decimal("10"));
    }

    private static ExchangeAccountCredentialSummary credential() {
        return new ExchangeAccountCredentialSummary(
                CREDENTIAL, ACCOUNT, "OKX_API_V5", "masked", "ACTIVE", "VERIFIED", true,
                null, null, null, NOW.minusSeconds(700), null, NOW.minusSeconds(600),
                "SUCCEEDED", "TRADE", false, "PASSED", 0, NOW.minusSeconds(600), null);
    }

    private static InstrumentCatalogItem instrument() {
        return new InstrumentCatalogItem(
                101L, "OKX", "SPOT", "BTC-USDT", "BTC-USDT", "BTC", "USDT", "LIVE",
                new BigDecimal("0.1"), new BigDecimal("0.001"), new BigDecimal("0.001"),
                new BigDecimal("100"), new BigDecimal("100000"), "USDT",
                new BigDecimal("1000000"), new BigDecimal("1000000"), "OKX_PUBLIC_INSTRUMENTS",
                "okx-public-instruments.v1", NOW.minusSeconds(60), NOW.minusSeconds(59), null,
                DIGEST_A, NOW.minusSeconds(59), NOW.minusSeconds(59));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(8);
    }
}
