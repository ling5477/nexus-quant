package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import java.time.Clock;
import java.util.Objects;

/**
 * Future worker 在 CLAIM、SEND 和 envelope acceptance 时共用的 current-kill gate。
 *
 * <p>每次调用都重新读取唯一 durable kill source；因此 claim 后、send 前发生 ENGAGED/version change
 * 时，旧 envelope 会被拒绝。该类没有发送或状态写入能力。</p>
 */
public final class WorkerOperationSafetyGate {

    private final KillSwitchService killSwitchService;
    private final KillSwitchPropagationPolicy propagationPolicy;
    private final Clock clock;

    public WorkerOperationSafetyGate(
            KillSwitchService killSwitchService,
            KillSwitchPropagationPolicy propagationPolicy,
            Clock clock
    ) {
        this.killSwitchService = Objects.requireNonNull(killSwitchService);
        this.propagationPolicy = Objects.requireNonNull(propagationPolicy);
        this.clock = Objects.requireNonNull(clock);
    }

    public Decision authorize(Phase phase, KillSwitchPropagationEnvelope envelope) {
        Objects.requireNonNull(phase, "phase must not be null");
        KillSwitchPropagationPolicy.Decision result = propagationPolicy.evaluate(
                envelope, killSwitchService.snapshot(), clock.instant());
        return result.status() == KillSwitchPropagationPolicy.Status.ALLOWED
                ? Decision.allowed(phase)
                : Decision.denied(phase, result.reason());
    }

    public record Decision(
            Phase phase,
            Status status,
            KillSwitchPropagationPolicy.Reason reason,
            boolean tradingAuthorization
    ) {
        public Decision {
            Objects.requireNonNull(phase);
            Objects.requireNonNull(status);
            tradingAuthorization = false;
            if ((status == Status.ALLOWED) != (reason == null)) {
                throw new IllegalArgumentException("operation status and reason are inconsistent");
            }
        }
        public static Decision allowed(Phase phase) { return new Decision(phase, Status.ALLOWED, null, false); }
        public static Decision denied(Phase phase, KillSwitchPropagationPolicy.Reason reason) {
            return new Decision(phase, Status.DENIED, Objects.requireNonNull(reason), false);
        }
    }

    public enum Phase { ENVELOPE_ACCEPTANCE, CLAIM, SEND }
    public enum Status { ALLOWED, DENIED }
}
