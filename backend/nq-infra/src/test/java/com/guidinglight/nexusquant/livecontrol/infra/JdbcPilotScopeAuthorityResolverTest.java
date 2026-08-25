package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeAuthorityResolver;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.application.MinimalPilotMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionState;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionStateRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * GateY-6D production authority resolver 的 exact SoR 与 server-owned scope 回归。
 */
class JdbcPilotScopeAuthorityResolverTest {

    private static final Instant NOW = Instant.parse("2026-08-16T08:00:00Z");
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String C = "c".repeat(64);
    private static final String D = "d".repeat(64);

    private ExchangeAccountRepository accounts;
    private ExchangeAccountCredentialRepository credentials;
    private StrategyReleaseAdmissionStateRepository admissions;
    private LiveControlRepository liveControl;
    private StandardEnvironment environment;
    private Map<String, Object> properties;
    private RiskLimitSet risk;
    private PilotScopeMaterializationCommand command;
    private PilotScopeAuthorityResolver.ResolvedScopeBindings bindings;

    @BeforeEach
    void setUp() {
        accounts = mock(ExchangeAccountRepository.class);
        credentials = mock(ExchangeAccountCredentialRepository.class);
        admissions = mock(StrategyReleaseAdmissionStateRepository.class);
        liveControl = mock(LiveControlRepository.class);
        environment = new StandardEnvironment();
        properties = new HashMap<>();
        environment.getPropertySources().addFirst(new MapPropertySource("gatey6d-test", properties));
        risk = new RiskLimitSet(
                UUID.randomUUID(), 1, new BigDecimal("1000"), new BigDecimal("100"),
                new BigDecimal("500"), new BigDecimal("50"), new BigDecimal("100"),
                3, 20, List.of("BTC-USDT"), 3600, new BigDecimal("20"), new BigDecimal("30"),
                1000, 9000, 11, NOW);
        command = command();
        bindings = bindings();
        when(accounts.findByIdForOwner(11L, 101L)).thenReturn(Optional.of(new ExchangeAccountSummary(
                101L, null, 11L, "OKX", "LIVE", "pilot", "immutable-account", false, "ACTIVE")));
        when(accounts.findById(101L)).thenReturn(Optional.of(new ExchangeAccountSummary(
                101L, null, 11L, "OKX", "LIVE", "pilot", "immutable-account", false, "ACTIVE")));
        when(credentials.findByCredentialIdForOwner(11L, 101L, 202L)).thenReturn(Optional.of(credential("TRADE")));
        when(admissions.loadByPublishRecordId("release-immutable-1")).thenReturn(new StrategyReleaseAdmissionState(
                "release-immutable-1", 7, 1, A, B, "strategy-release-manifest.v1", NOW, NOW, NOW));
        when(liveControl.findRiskLimitSet(risk.id())).thenReturn(Optional.of(risk));
    }

    @Test
    void shouldResolveMinimalOperatorAuthorityWithoutStrategyOrRiskFacts() {
        configureRuntime(bindings);
        MinimalPilotMaterializationCommand minimal = new MinimalPilotMaterializationCommand(
                UUID.randomUUID(), UUID.randomUUID(), 101, 202, "BTC-USDT",
                new BigDecimal("10.00000000"), NOW, NOW.plusSeconds(120),
                "idem-minimal", "request-minimal", "trace-minimal");

        var resolved = resolver().resolveMinimal(new AuthenticatedLiveControlActor(11), minimal);

        OperatorPilotAuthority authority = resolved.operatorPilotAuthority();
        assertEquals(11, resolved.ownerId());
        assertEquals("BTC-USDT", authority.instrument());
        assertEquals(OperatorPilotAuthority.Side.BUY, authority.side());
        assertEquals(OperatorPilotAuthority.OrderType.LIMIT, authority.orderType());
        assertEquals(new BigDecimal("10.00000000"), authority.maxNotional());
        assertEquals(1, authority.maxPlaceCount());
        assertEquals(1, authority.maxCancelCount());
        assertEquals(bindings, resolved.scopeBindings());
        verifyNoInteractions(admissions, liveControl);
    }

    @Test
    void shouldResolveStoredRiskAndServerOwnedScopeBindings() {
        configureRuntime(bindings);

        var resolved = resolver().resolve(new AuthenticatedLiveControlActor(11), command);

        assertEquals(risk, resolved.riskLimitSet());
        assertEquals(bindings, resolved.scopeBindings());
    }

    @Test
    void shouldRejectCredentialThatIsNotGateY6cAcceptedTradePermission() {
        configureRuntime(bindings);
        when(credentials.findByCredentialIdForOwner(11L, 101L, 202L))
                .thenReturn(Optional.of(credential("READ_ONLY")));

        LiveControlException failure = assertThrows(
                LiveControlException.class,
                () -> resolver().resolve(new AuthenticatedLiveControlActor(11), command));

        assertEquals("PILOT_CREDENTIAL_REFERENCE_MISMATCH", failure.code());
    }

    @Test
    void shouldRejectReleaseAndRiskMismatch() {
        configureRuntime(bindings);
        when(admissions.loadByPublishRecordId("release-immutable-1")).thenReturn(new StrategyReleaseAdmissionState(
                "release-immutable-1", 8, 1, A, B, "strategy-release-manifest.v1", NOW, NOW, NOW));
        LiveControlException releaseFailure = assertThrows(
                LiveControlException.class,
                () -> resolver().resolve(new AuthenticatedLiveControlActor(11), command));
        assertEquals("PILOT_RELEASE_REFERENCE_MISMATCH", releaseFailure.code());

        when(admissions.loadByPublishRecordId("release-immutable-1")).thenReturn(new StrategyReleaseAdmissionState(
                "release-immutable-1", 7, 1, A, B, "strategy-release-manifest.v1", NOW, NOW, NOW));
        when(liveControl.findRiskLimitSet(risk.id())).thenReturn(Optional.empty());
        LiveControlException riskFailure = assertThrows(
                LiveControlException.class,
                () -> resolver().resolve(new AuthenticatedLiveControlActor(11), command));
        assertEquals("PILOT_RISK_REFERENCE_MISMATCH", riskFailure.code());
    }

    @Test
    void shouldRejectMissingRuntimeAuthorityInsteadOfTrustingOperator() {
        LiveControlException failure = assertThrows(
                LiveControlException.class,
                () -> resolver().resolve(new AuthenticatedLiveControlActor(11), command));

        assertEquals("PILOT_RUNTIME_AUTHORITY_NOT_CONFIGURED", failure.code());
    }

    @Test
    void shouldRejectMalformedOrDriftingRuntimeAuthority() {
        configureRuntime(bindings);
        properties.put(prefix("balance-maximum-age-ms"), "not-a-number");
        LiveControlException malformed = assertThrows(
                LiveControlException.class,
                () -> resolver().resolve(new AuthenticatedLiveControlActor(11), command));
        assertEquals("PILOT_RUNTIME_AUTHORITY_NOT_CONFIGURED", malformed.code());

        properties.put(prefix("balance-maximum-age-ms"), String.valueOf(bindings.balanceMaximumAgeMs()));
        properties.put(prefix("worker-identity"), "latest");
        LiveControlException drifting = assertThrows(
                LiveControlException.class,
                () -> resolver().resolve(new AuthenticatedLiveControlActor(11), command));
        assertEquals("PILOT_RUNTIME_AUTHORITY_NOT_CONFIGURED", drifting.code());
    }

    private JdbcPilotScopeAuthorityResolver resolver() {
        return new JdbcPilotScopeAuthorityResolver(accounts, credentials, admissions, liveControl, environment);
    }

    private PilotScopeMaterializationCommand command() {
        var riskSelection = new PilotScopeMaterializationCommand.RiskSelection(
                risk.id(), risk.canonicalDigest(), 1, risk.capitalCap(), risk.maxOrderNotional(),
                risk.maxSymbolPositionNotional(), risk.maxDailyRealizedLoss(), risk.maxDailyTotalLoss(),
                risk.maxOpenOrders(), risk.maxIntradayOrders(), risk.symbolAllowlist(),
                risk.maxSessionDurationSeconds(), risk.spreadLimitBps(), risk.slippageLimitBps(),
                risk.maxMarketDataAgeMs(), risk.minDataCoverageBps());
        return new PilotScopeMaterializationCommand(
                UUID.randomUUID(), UUID.randomUUID(), 101, 202, "release-immutable-1", A, 7,
                riskSelection, List.of("BTC-USDT"), new BigDecimal("500"), NOW.plusSeconds(60),
                NOW.plusSeconds(3600), B, "idem", "request", "trace");
    }

    private static PilotScopeAuthorityResolver.ResolvedScopeBindings bindings() {
        return new PilotScopeAuthorityResolver.ResolvedScopeBindings(
                A, "instrument-source@1", "instrument.v1", 60_000, B, "tier-1",
                PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE, "fee-source@1", "fee.v1", 60_000,
                "balance-source@1", "balance.v1", 5_000, "clock-source@1", "clock.v1", 5_000,
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, 100, "endpoint-policy.v1", C,
                "provider-contract.v1", D, "worker@1", A);
    }

    private ExchangeAccountCredentialSummary credential(String permissionScope) {
        return new ExchangeAccountCredentialSummary(
                202L, 101L, "OKX_API_V5", "masked", "ACTIVE", "VERIFIED", true,
                null, null, null, NOW, null, NOW, "SUCCEEDED", permissionScope, false,
                "PASSED", 0, NOW, null);
    }

    private void configureRuntime(PilotScopeAuthorityResolver.ResolvedScopeBindings value) {
        properties.put(prefix("instrument-metadata-digest"), value.instrumentMetadataDigest());
        properties.put(prefix("instrument-source-identity"), value.instrumentSourceIdentity());
        properties.put(prefix("instrument-source-schema-version"), value.instrumentSourceSchemaVersion());
        properties.put(prefix("instrument-maximum-age-ms"), String.valueOf(value.instrumentMaximumAgeMs()));
        properties.put(prefix("fee-schedule-digest"), value.feeScheduleDigest());
        properties.put(prefix("fee-tier"), value.feeTier());
        properties.put(prefix("fee-evidence-class"), value.feeEvidenceClass().name());
        properties.put(prefix("fee-source-identity"), value.feeSourceIdentity());
        properties.put(prefix("fee-source-schema-version"), value.feeSourceSchemaVersion());
        properties.put(prefix("fee-maximum-age-ms"), String.valueOf(value.feeMaximumAgeMs()));
        properties.put(prefix("balance-source-identity"), value.balanceSourceIdentity());
        properties.put(prefix("balance-source-schema-version"), value.balanceSourceSchemaVersion());
        properties.put(prefix("balance-maximum-age-ms"), String.valueOf(value.balanceMaximumAgeMs()));
        properties.put(prefix("clock-source-identity"), value.clockSourceIdentity());
        properties.put(prefix("clock-source-schema-version"), value.clockSourceSchemaVersion());
        properties.put(prefix("clock-maximum-age-ms"), String.valueOf(value.clockMaximumAgeMs()));
        properties.put(prefix("signed-timestamp-source"), value.signedTimestampSource());
        properties.put(prefix("maximum-tolerated-skew-ms"), String.valueOf(value.maximumToleratedSkewMs()));
        properties.put(prefix("endpoint-policy-version"), value.endpointPolicyVersion());
        properties.put(prefix("endpoint-policy-digest"), value.endpointPolicyDigest());
        properties.put(prefix("provider-contract-identity"), value.providerContractIdentity());
        properties.put(prefix("provider-artifact-digest"), value.providerArtifactDigest());
        properties.put(prefix("worker-identity"), value.workerIdentity());
        properties.put(prefix("worker-release-digest"), value.workerReleaseDigest());
    }

    private static String prefix(String key) {
        return "nq.live-control.pilot-materialization." + key;
    }
}
