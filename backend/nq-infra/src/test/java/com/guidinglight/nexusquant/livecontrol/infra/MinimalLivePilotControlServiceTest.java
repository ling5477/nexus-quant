package com.guidinglight.nexusquant.livecontrol.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.account.application.CredentialPermissionProbeService;
import com.guidinglight.nexusquant.account.application.command.CredentialPermissionProbeCommand;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionProbeSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingValidation;
import com.guidinglight.nexusquant.livecontrol.application.MinimalLivePilotCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotExecutionLeaseControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationResult;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.PilotExecutionLease;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MinimalLivePilotControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T04:00:00Z");
    private static final UUID SCOPE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SET_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void refreshesPermissionAndDerivesExactPriceAndDownwardQuantityFromFiveFacts() {
        Fixture fixture = fixture(new BigDecimal("0.00100000"));

        var permit = fixture.service.prepare(command());

        ArgumentCaptor<CredentialPermissionProbeCommand> probe =
                ArgumentCaptor.forClass(CredentialPermissionProbeCommand.class);
        verify(fixture.permissionProbeService).probe(
                anyLong(), anyLong(), anyLong(), anyString(), probe.capture(), anyString());
        assertEquals("GATEY_PILOT_READINESS", probe.getValue().mode());
        assertEquals(Boolean.TRUE, probe.getValue().dryRun());
        assertEquals(Boolean.TRUE, probe.getValue().paperSafetyConfirmed());

        ArgumentCaptor<ExactPilotBindingCommand> binding =
                ArgumentCaptor.forClass(ExactPilotBindingCommand.class);
        verify(fixture.bindings).create(any(), binding.capture());
        assertEquals(0, binding.getValue().order().price().compareTo(new BigDecimal("100.00000000")));
        assertEquals(0, binding.getValue().order().quantity().compareTo(new BigDecimal("0.09800000")));
        assertEquals(0, binding.getValue().order().notional().compareTo(new BigDecimal("9.80000000")));
        assertEquals(0, permit.limitPrice().compareTo(binding.getValue().order().price()));
        assertEquals(0, permit.quantity().compareTo(binding.getValue().order().quantity()));
        assertEquals(0, permit.notional().compareTo(binding.getValue().order().notional()));
    }

    @Test
    void blocksBeforeBindingWhenVenueMinimumCannotFitFeeReserveAndBufferUnderTenUsdt() {
        Fixture fixture = fixture(new BigDecimal("0.10000000"));

        LiveControlException failure = assertThrows(
                LiveControlException.class, () -> fixture.service.prepare(command()));

        assertEquals("BTC_USDT_VENUE_MINIMUM_EXCEEDS_PILOT_CAP", failure.code());
        verify(fixture.bindings, never()).create(any(), any());
    }

    private static Fixture fixture(BigDecimal minimumQuantity) {
        ExchangeAccountRepository accounts = mock(ExchangeAccountRepository.class);
        InstrumentCatalogReadPort catalog = mock(InstrumentCatalogReadPort.class);
        CredentialPermissionProbeService permissionProbeService = mock(CredentialPermissionProbeService.class);
        PilotScopeControlPlane scopes = mock(PilotScopeControlPlane.class);
        PilotScopeRepository scopeRepository = mock(PilotScopeRepository.class);
        ExactPilotBindingControlPlane bindings = mock(ExactPilotBindingControlPlane.class);
        PilotExecutionLeaseControlPlane leases = mock(PilotExecutionLeaseControlPlane.class);

        when(accounts.findById(1L)).thenReturn(Optional.of(new ExchangeAccountSummary(
                1L, 101L, 7L, "OKX", "LIVE", "pilot", "reference", true, "ACTIVE")));
        when(permissionProbeService.probe(
                anyLong(), anyLong(), anyLong(), anyString(), any(), anyString()))
                .thenReturn(new CredentialPermissionProbeSummary(
                        1L, 2L, "OKX_API_V5", "OKX", "SUCCEEDED", "TRADE", false,
                        "PASSED", 0, NOW, null, "permission-request", "permission-trace"));
        when(scopes.materializeMinimal(any(), any())).thenReturn(
                new PilotScopeMaterializationResult(UUID.randomUUID(), SCOPE_ID, SET_ID, "a".repeat(64)));
        when(scopeRepository.findObservationSet(SCOPE_ID, SET_ID))
                .thenReturn(Optional.of(observations(minimumQuantity)));
        when(catalog.findByExchangeAndSymbols("OKX", List.of("BTC-USDT")))
                .thenReturn(List.of(catalogItem(minimumQuantity)));

        ExactPilotBinding storedBinding = mock(ExactPilotBinding.class);
        UUID bindingId = UUID.randomUUID();
        when(storedBinding.id()).thenReturn(bindingId);
        when(storedBinding.bindingDigest()).thenReturn("b".repeat(64));
        when(bindings.create(any(), any())).thenReturn(storedBinding);
        when(bindings.validate(any(), any(), any())).thenReturn(new ExactPilotBindingValidation(
                bindingId, ExactPilotBinding.Lifecycle.VERIFIED, NOW, List.of(), false));
        PilotExecutionLease lease = mock(PilotExecutionLease.class);
        when(lease.id()).thenReturn(UUID.randomUUID());
        when(leases.createAndActivate(any(), any(), any(), any(), any())).thenReturn(lease);

        return new Fixture(new MinimalLivePilotControlService(
                accounts, catalog, permissionProbeService, scopes, scopeRepository,
                bindings, leases, Clock.fixed(NOW, ZoneOffset.UTC)),
                permissionProbeService, bindings);
    }

    private static MinimalLivePilotCommand command() {
        return new MinimalLivePilotCommand(
                1L, 2L, "BTC-USDT", ExactPilotBinding.Side.BUY, new BigDecimal("10.00000000"));
    }

    private static InstrumentCatalogItem catalogItem(BigDecimal minimumQuantity) {
        return new InstrumentCatalogItem(
                77L, "OKX", "SPOT", "BTC-USDT", "BTC-USDT", "BTC", "USDT", "LIVE",
                new BigDecimal("0.10000000"), new BigDecimal("0.00100000"), minimumQuantity,
                "OKX_ACCOUNT_INSTRUMENTS", NOW, NOW, NOW);
    }

    private static PilotObservationSet observations(BigDecimal minimumQuantity) {
        var item = new PilotPrerequisiteObservation.InstrumentItem(
                "BTC-USDT", PilotPrerequisiteObservation.TradingStatus.LIVE,
                new BigDecimal("0.10000000"), new BigDecimal("0.00100000"), minimumQuantity,
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_NOT_PUBLISHED,
                null, null);
        var instrument = canonical(new PilotPrerequisiteObservation.InstrumentMetadata(
                envelope("instrument", PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION,
                        "OKX_ACCOUNT_INSTRUMENTS", "okx-account-instruments.v5", NOW),
                PilotObservationCanonicalEncoder.instrumentMetadataDigest(List.of(item)), List.of(item)));
        var fee = canonical(new PilotPrerequisiteObservation.FeeSchedule(
                envelope("fee", PilotPrerequisiteObservation.FeeSchedule.SCHEMA_VERSION,
                        "OKX_ACCOUNT_TRADE_FEE", "okx-account-trade-fee.v5", NOW),
                PilotObservationCanonicalEncoder.feeScheduleDigest(
                        List.of("BTC-USDT"), "Lv1/1", new BigDecimal("-0.0008"), new BigDecimal("-0.001")),
                "Lv1/1", PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE,
                new BigDecimal("-0.0008"), new BigDecimal("-0.001"),
                PilotPrerequisiteObservation.FeeSchedule.LOSS_TREATMENT));
        var balance = canonical(new PilotPrerequisiteObservation.BalanceSnapshot(
                envelope("balance", PilotPrerequisiteObservation.BalanceSnapshot.SCHEMA_VERSION,
                        "OKX_ACCOUNT_BALANCE", "okx-account-balance.v5", NOW),
                PilotObservationCanonicalEncoder.balanceSnapshotDigest(new BigDecimal("10.00000000")),
                "USDT", new BigDecimal("10.00000000")));
        var clock = canonical(new PilotPrerequisiteObservation.ClockSync(
                envelope("clock", PilotPrerequisiteObservation.ClockSync.SCHEMA_VERSION,
                        "OKX_PUBLIC_TIME", "okx-public-time.v5", NOW),
                PilotObservationCanonicalEncoder.clockSyncDigest(
                        PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, 0),
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, 0));
        String marketDigest = PilotObservationCanonicalEncoder.marketSnapshotDigest(
                "BTC-USDT", new BigDecimal("100.00000000"), NOW,
                "OKX_MARKET_TICKER", "okx-market-ticker.v5");
        var market = canonical(new PilotPrerequisiteObservation.MarketSnapshot(
                envelope("market", PilotPrerequisiteObservation.MarketSnapshot.SCHEMA_VERSION,
                        "OKX_MARKET_TICKER", "okx-market-ticker.v5", NOW),
                marketDigest, "BTC-USDT", new BigDecimal("100.00000000")));
        return new PilotObservationSet(SET_ID, SCOPE_ID, instrument, fee, balance, clock, market);
    }

    private static PilotPrerequisiteObservation.Envelope envelope(
            String identity,
            String observationSchema,
            String source,
            String sourceSchema,
            Instant observedAt
    ) {
        return new PilotPrerequisiteObservation.Envelope(
                UUID.randomUUID(), SCOPE_ID, SET_ID, observationSchema, identity,
                source, sourceSchema, observedAt, NOW, "worker", "0".repeat(64));
    }

    private static PilotPrerequisiteObservation.InstrumentMetadata canonical(
            PilotPrerequisiteObservation.InstrumentMetadata value
    ) {
        return new PilotPrerequisiteObservation.InstrumentMetadata(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.instrumentMetadataDigest(), value.items());
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

    private static PilotPrerequisiteObservation.MarketSnapshot canonical(
            PilotPrerequisiteObservation.MarketSnapshot value
    ) {
        return new PilotPrerequisiteObservation.MarketSnapshot(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.marketSnapshotDigest(), value.instrument(), value.bestAsk());
    }

    private record Fixture(
            MinimalLivePilotControlService service,
            CredentialPermissionProbeService permissionProbeService,
            ExactPilotBindingControlPlane bindings
    ) {
    }
}
