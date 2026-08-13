package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Worker claim/send/start 共用的 kill propagation fail-closed policy。
 */
public final class KillSwitchPropagationPolicy {

    private final Duration maximumAge;

    public KillSwitchPropagationPolicy(Duration maximumAge) {
        this.maximumAge = Objects.requireNonNull(maximumAge, "maximumAge must not be null");
        if (maximumAge.isZero() || maximumAge.isNegative()) {
            throw new IllegalArgumentException("maximumAge must be positive");
        }
    }

    public Decision evaluate(
            KillSwitchPropagationEnvelope envelope,
            KillSwitchSnapshot current,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        if (envelope == null) return Decision.denied(Reason.MISSING);
        if (!KillSwitchPropagationEnvelope.SCHEMA_VERSION.equals(envelope.schemaVersion())
                || !envelope.hasValidDigest()) return Decision.denied(Reason.CONFLICT);
        if (isStale(envelope.observedAt(), now)) return Decision.denied(Reason.STALE);
        if (envelope.status() == KillSwitchStatus.ENGAGED) return Decision.denied(Reason.ENGAGED);
        if (envelope.status() == KillSwitchStatus.UNKNOWN) return Decision.denied(Reason.UNKNOWN);
        if (current == null) return Decision.denied(Reason.MISSING);
        if (isStale(current.observedAt(), now)) return Decision.denied(Reason.STALE);
        if (current.status() == KillSwitchStatus.ENGAGED) return Decision.denied(Reason.ENGAGED);
        if (current.status() == KillSwitchStatus.UNKNOWN) return Decision.denied(Reason.UNKNOWN);
        if (!current.scope().name().equals(envelope.scope())
                || current.status() != envelope.status()
                || current.version() != envelope.version()
                || !Objects.equals(current.updatedAt(), envelope.stateUpdatedAt())
                || !current.source().equals(envelope.source())) {
            return Decision.denied(Reason.CONFLICT);
        }
        return Decision.allowed();
    }

    private boolean isStale(Instant observedAt, Instant now) {
        return observedAt == null || observedAt.isAfter(now)
                || Duration.between(observedAt, now).compareTo(maximumAge) > 0;
    }

    public record Decision(Status status, Reason reason) {
        public Decision {
            Objects.requireNonNull(status);
            if ((status == Status.ALLOWED) != (reason == null)) {
                throw new IllegalArgumentException("allowed status and reason are inconsistent");
            }
        }
        public static Decision allowed() { return new Decision(Status.ALLOWED, null); }
        public static Decision denied(Reason reason) { return new Decision(Status.DENIED, reason); }
    }

    public enum Status { ALLOWED, DENIED }
    public enum Reason { ENGAGED, UNKNOWN, MISSING, STALE, CONFLICT }
}
