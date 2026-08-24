package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.application.PilotExecutionLeaseControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionCommand;
import com.guidinglight.nexusquant.livecontrol.domain.PilotExecutionLease;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotExecutionLeaseRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.PilotKillSwitchDisengageCommand;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lease、既有LiveSession状态机与global kill的fail-closed编排。
 *
 * <p>任何步骤失败都会尽力关闭lease并engage kill；真正send入口仍独立重读三类durable facts，
 * 因此进程在finally前崩溃也不能仅凭DISENGAGED继续PLACE。</p>
 */
public final class PilotExecutionLeaseService implements PilotExecutionLeaseControlPlane {

    private final PilotExecutionLeaseRepository leases;
    private final LiveSessionControlService sessions;
    private final KillSwitchService killSwitch;
    private final Clock clock;

    public PilotExecutionLeaseService(
            PilotExecutionLeaseRepository leases,
            LiveSessionControlService sessions,
            KillSwitchService killSwitch,
            Clock clock
    ) {
        this.leases = Objects.requireNonNull(leases, "leases must not be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.killSwitch = Objects.requireNonNull(killSwitch, "killSwitch must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public PilotExecutionLease createAndActivate(
            AuthenticatedLiveControlActor actor,
            ExactPilotBinding binding,
            BigDecimal maxNotional,
            Instant expiresAt,
            ExactPilotBinding.Correlation correlation
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(correlation, "correlation must not be null");
        Instant now = clock.instant();
        PilotExecutionLease created = leases.create(PilotExecutionLease.created(
                        UUID.randomUUID(), binding, maxNotional, actor.userId(), now, expiresAt),
                correlation.requestId(), correlation.traceId());
        try {
            transition(actor, binding.sessionId(), LiveSessionCommand.APPROVE, correlation);
            transition(actor, binding.sessionId(), LiveSessionCommand.START, correlation);
            PilotExecutionLease active = leases.activate(
                    created.id(), created.version(), clock.instant(), correlation.requestId(), correlation.traceId());
            var kill = killSwitch.snapshot();
            if (kill.status() != KillSwitchStatus.ENGAGED) {
                throw new LiveControlException("PILOT_KILL_NOT_ENGAGED", "pilot must start from ENGAGED");
            }
            killSwitch.disengageForPilot(new PilotKillSwitchDisengageCommand(
                    KillSwitchScope.GLOBAL_TRADING, kill.version(), active.id(), active.expiresAt(),
                    "OPERATOR_" + actor.userId(), correlation.traceId(), clock.instant()));
            transition(actor, binding.sessionId(), LiveSessionCommand.ACTIVATE, correlation);
            return active;
        } catch (RuntimeException failure) {
            recoverLease(actor, created.id(), binding.sessionId(), correlation, "PILOT_ACTIVATION_FAILED");
            throw failure;
        }
    }

    @Override
    public PilotExecutionLease bindPlace(
            AuthenticatedLiveControlActor actor,
            UUID leaseId,
            UUID intentId,
            ExactPilotBinding binding,
            ExactPilotBinding.Correlation correlation
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (binding.account().ownerId() != actor.userId()) {
            throw new LiveControlException("PILOT_LEASE_OWNER_MISMATCH", "binding owner differs from operator");
        }
        return leases.bindPlaceAndConsume(
                leaseId, intentId, binding, clock.instant(), correlation.requestId(), correlation.traceId());
    }

    @Override
    public void bindCancel(UUID leaseId, UUID intentId) {
        leases.bindCancel(leaseId, intentId, clock.instant());
    }

    @Override
    public PilotExecutionLease close(
            AuthenticatedLiveControlActor actor,
            UUID leaseId,
            PilotExecutionLease.Status terminal,
            String reasonCode,
            ExactPilotBinding.Correlation correlation
    ) {
        PilotExecutionLease current = leases.find(leaseId)
                .orElseThrow(() -> new LiveControlException("PILOT_LEASE_NOT_FOUND", "lease was not found"));
        try {
            try {
                transition(actor, current.liveSessionId(), LiveSessionCommand.STOP, correlation);
                transition(actor, current.liveSessionId(), LiveSessionCommand.BEGIN_RECONCILE, correlation);
                transition(actor, current.liveSessionId(),
                        terminal == PilotExecutionLease.Status.CLOSED
                                ? LiveSessionCommand.RECONCILE_PASS : LiveSessionCommand.RECONCILE_BLOCK,
                        correlation);
            } catch (RuntimeException sessionFailure) {
                if (terminal == PilotExecutionLease.Status.CLOSED) throw sessionFailure;
            }
            return leases.close(leaseId, terminal, clock.instant(), reasonCode,
                    correlation.requestId(), correlation.traceId());
        } finally {
            engageIfRequired(reasonCode, correlation.traceId());
        }
    }

    @Override
    public void recoverAtStartup() {
        Instant now = clock.instant();
        for (PilotExecutionLease lease : leases.findRecoverable(now)) {
            if (lease.status() == PilotExecutionLease.Status.CONSUMED) {
                continue;
            }
            PilotExecutionLease.Status terminal = now.isBefore(lease.expiresAt())
                    ? PilotExecutionLease.Status.FAILED : PilotExecutionLease.Status.EXPIRED;
            try {
                leases.close(lease.id(), terminal, now, "PILOT_STARTUP_RECOVERY",
                        "pilot-startup-recovery", "pilot-startup-recovery");
            } catch (RuntimeException ignored) {
                // 继续执行global engage；execution send gate会独立拒绝非ACTIVE/过期lease。
            }
        }
        engageIfRequired("PILOT_STARTUP_RECOVERY", "pilot-startup-recovery");
    }

    @Override
    public java.util.Optional<PilotExecutionLease> findConsumedForRecovery() {
        return leases.findRecoverable(clock.instant()).stream()
                .filter(value -> value.status() == PilotExecutionLease.Status.CONSUMED)
                .findFirst();
    }

    @Override
    public PilotExecutionLease resumeConsumed(
            AuthenticatedLiveControlActor actor,
            PilotExecutionLease lease,
            ExactPilotBinding.Correlation correlation
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(lease, "lease must not be null");
        if (lease.createdBy() != actor.userId() || lease.status() != PilotExecutionLease.Status.CONSUMED) {
            throw new LiveControlException("PILOT_RECOVERY_LEASE_REJECTED", "consumed lease cannot be resumed");
        }
        if (!clock.instant().isBefore(lease.expiresAt())) return lease;
        var kill = killSwitch.snapshot();
        if (kill.status() != KillSwitchStatus.ENGAGED) {
            throw new LiveControlException("PILOT_KILL_NOT_ENGAGED", "recovery must start from ENGAGED");
        }
        killSwitch.disengageForPilot(new PilotKillSwitchDisengageCommand(
                KillSwitchScope.GLOBAL_TRADING, kill.version(), lease.id(), lease.expiresAt(),
                "OPERATOR_" + actor.userId(), correlation.traceId(), clock.instant()));
        return lease;
    }

    @Override
    public void suspendConsumedForRecovery(
            AuthenticatedLiveControlActor actor,
            PilotExecutionLease lease,
            String reasonCode,
            ExactPilotBinding.Correlation correlation
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(lease, "lease must not be null");
        Objects.requireNonNull(correlation, "correlation must not be null");
        if (lease.createdBy() != actor.userId() || lease.status() != PilotExecutionLease.Status.CONSUMED) {
            throw new LiveControlException(
                    "PILOT_RECOVERY_LEASE_REJECTED", "only the exact consumed lease can be suspended");
        }
        engageIfRequired(reasonCode, correlation.traceId());
    }

    private void recoverLease(
            AuthenticatedLiveControlActor actor,
            UUID leaseId,
            UUID sessionId,
            ExactPilotBinding.Correlation correlation,
            String reason
    ) {
        try {
            var session = sessions.transitionMinimalPilot(
                    actor, sessionId, LiveSessionCommand.STOP,
                    correlation.requestId(), correlation.traceId(), correlation.idempotencyKey());
            sessions.transitionMinimalPilot(
                    actor, session.id(), LiveSessionCommand.BEGIN_RECONCILE,
                    correlation.requestId(), correlation.traceId(), correlation.idempotencyKey());
        } catch (RuntimeException ignored) {
            // Lease/kill recovery remains mandatory even if the session is not ACTIVE.
        }
        try {
            PilotExecutionLease lease = leases.find(leaseId).orElse(null);
            if (lease != null && (lease.status() == PilotExecutionLease.Status.CREATED
                    || lease.status() == PilotExecutionLease.Status.ACTIVE
                    || lease.status() == PilotExecutionLease.Status.CONSUMED)) {
                leases.close(leaseId, PilotExecutionLease.Status.FAILED, clock.instant(), reason,
                        correlation.requestId(), correlation.traceId());
            }
        } finally {
            engageIfRequired(reason, correlation.traceId());
        }
    }

    private void engageIfRequired(String reason, String traceId) {
        var snapshot = killSwitch.snapshot();
        if (snapshot.status() == KillSwitchStatus.DISENGAGED) {
            killSwitch.engage(snapshot.version(), reason, "PILOT_RECOVERY", traceId);
        }
    }

    private void transition(
            AuthenticatedLiveControlActor actor,
            UUID sessionId,
            LiveSessionCommand command,
            ExactPilotBinding.Correlation correlation
    ) {
        sessions.transitionMinimalPilot(actor, sessionId, command,
                correlation.requestId(), correlation.traceId(), correlation.idempotencyKey());
    }
}
