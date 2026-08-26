package com.guidinglight.nexusquant.livecontrol.infra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.PilotExecutionLease;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotExecutionLeaseRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotPrePlaceRecoveryRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotPrePlaceRecoveryRepository.Authorization;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PilotExecutionLeaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    @Test
    void startupRecoveryExpiresLeaseAndReengagesKillBeforeExecution() {
        PilotExecutionLeaseRepository leases = mock(PilotExecutionLeaseRepository.class);
        LiveSessionControlService sessions = mock(LiveSessionControlService.class);
        KillSwitchService kill = mock(KillSwitchService.class);
        PilotExecutionLease active = lease(PilotExecutionLease.Status.ACTIVE, NOW.minusSeconds(1));
        when(leases.findRecoverable(NOW)).thenReturn(List.of(active));
        when(leases.close(eq(active.id()), eq(PilotExecutionLease.Status.EXPIRED), eq(NOW),
                eq("PILOT_STARTUP_RECOVERY"), any(), any())).thenReturn(lease(
                PilotExecutionLease.Status.EXPIRED, NOW.minusSeconds(1)));
        when(kill.snapshot()).thenReturn(new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING, KillSwitchStatus.DISENGAGED, 3,
                "PILOT", "PILOT_EXECUTION_LEASE", NOW.minusSeconds(2), NOW, "trace"));

        new PilotExecutionLeaseService(
                leases, sessions, kill, Clock.fixed(NOW, ZoneOffset.UTC)).recoverAtStartup();

        verify(leases).close(eq(active.id()), eq(PilotExecutionLease.Status.EXPIRED), eq(NOW),
                eq("PILOT_STARTUP_RECOVERY"), any(), any());
        verify(kill).engage(3, "PILOT_STARTUP_RECOVERY", "PILOT_RECOVERY", "pilot-startup-recovery");
    }

    @Test
    void consumedLeaseFailureReengagesKillWithoutClosingRecoveryFact() {
        PilotExecutionLeaseRepository leases = mock(PilotExecutionLeaseRepository.class);
        LiveSessionControlService sessions = mock(LiveSessionControlService.class);
        KillSwitchService kill = mock(KillSwitchService.class);
        PilotExecutionLease consumed = lease(PilotExecutionLease.Status.CONSUMED, NOW.plusSeconds(60));
        when(kill.snapshot()).thenReturn(new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING, KillSwitchStatus.DISENGAGED, 7,
                "PILOT_LEASE_" + consumed.id(), "PILOT_EXECUTION_LEASE", NOW.minusSeconds(2),
                consumed.expiresAt(), "trace"));
        var correlation = new ExactPilotBinding.Correlation("request", "trace", "idempotency");

        new PilotExecutionLeaseService(leases, sessions, kill, Clock.fixed(NOW, ZoneOffset.UTC))
                .suspendConsumedForRecovery(
                        new AuthenticatedLiveControlActor(consumed.createdBy()),
                        consumed,
                        "PILOT_RECONCILIATION_REQUIRED",
                        correlation);

        verify(kill).engage(7, "PILOT_RECONCILIATION_REQUIRED", "PILOT_RECOVERY", "trace");
        verify(leases, never()).close(any(), any(), any(), any(), any(), any());
    }

    @Test
    void zeroIntentDecisionTerminalizesOnlyTheExactPredecessorSession() {
        PilotExecutionLeaseRepository leases = mock(PilotExecutionLeaseRepository.class);
        PilotPrePlaceRecoveryRepository recoveries = mock(PilotPrePlaceRecoveryRepository.class);
        LiveSessionControlService sessions = mock(LiveSessionControlService.class);
        KillSwitchService kill = mock(KillSwitchService.class);
        var actor = new AuthenticatedLiveControlActor(11L);
        var correlation = new ExactPilotBinding.Correlation("request", "trace", "idempotency");
        Authorization authorization = new Authorization(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7);
        when(kill.snapshot()).thenReturn(new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING, KillSwitchStatus.ENGAGED, 4,
                "SAFE", "PILOT_RECOVERY", NOW, NOW, "trace"));
        when(recoveries.decide(
                eq(11L), eq(1L), eq(2L), eq("BTC-USDT"), any(), any(),
                eq("request"), eq("trace"), eq(NOW)))
                .thenReturn(Optional.of(authorization));

        new PilotExecutionLeaseService(
                leases, recoveries, sessions, kill, Clock.fixed(NOW, ZoneOffset.UTC))
                .prepareZeroIntentReplacement(
                        actor, 1L, 2L, "BTC-USDT", new BigDecimal("10.00000000"), correlation);

        verify(sessions).terminalizeMinimalPilotPrePlaceRecovery(
                actor, authorization.predecessorSessionId(), authorization.decisionId(),
                "request", "trace", "idempotency");
        verify(sessions).terminalizeExpiredMinimalPilotPreparation(
                actor, 1L, 2L, "BTC-USDT", new BigDecimal("10.00000000"),
                authorization.decisionId(), "request", "trace", "idempotency");
        verify(leases, never()).close(any(), any(), any(), any(), any(), any());
    }

    private static PilotExecutionLease lease(PilotExecutionLease.Status status, Instant expiresAt) {
        Instant consumedAt = status == PilotExecutionLease.Status.CONSUMED ? NOW.minusSeconds(2) : null;
        Instant closedAt = status == PilotExecutionLease.Status.EXPIRED ? NOW : null;
        return new PilotExecutionLease(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), status,
                new BigDecimal("1.00000000"), NOW.minusSeconds(60), expiresAt, consumedAt, closedAt,
                11, status == PilotExecutionLease.Status.ACTIVE ? 2 : 3, NOW.minusSeconds(60), NOW);
    }
}
