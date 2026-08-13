package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.risk.service.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchState;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** kill envelope acceptance、stale/conflict 与 claim-send race regression。 */
class KillSwitchPropagationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-13T02:00:00Z");
    private final KillSwitchPropagationPolicy policy = new KillSwitchPropagationPolicy(Duration.ofSeconds(30));

    @Test
    void shouldAcceptOnlyFreshConsistentDisengagedEnvelope() {
        KillSwitchSnapshot current = snapshot(KillSwitchStatus.DISENGAGED, 1, NOW.minusSeconds(2), NOW);
        KillSwitchPropagationEnvelope envelope = KillSwitchPropagationEnvelope.fromSnapshot(current);

        assertEquals(KillSwitchPropagationPolicy.Status.ALLOWED,
                policy.evaluate(envelope, current, NOW).status());
        assertEquals(KillSwitchPropagationPolicy.Reason.MISSING,
                policy.evaluate(null, current, NOW).reason());
        assertEquals(KillSwitchPropagationPolicy.Reason.ENGAGED,
                policy.evaluate(KillSwitchPropagationEnvelope.fromSnapshot(
                        snapshot(KillSwitchStatus.ENGAGED, 2, NOW.minusSeconds(1), NOW)), current, NOW).reason());
        assertEquals(KillSwitchPropagationPolicy.Reason.UNKNOWN,
                policy.evaluate(KillSwitchPropagationEnvelope.fromSnapshot(unknown()), current, NOW).reason());
        assertEquals(KillSwitchPropagationPolicy.Reason.STALE,
                policy.evaluate(envelope, current, NOW.plusSeconds(31)).reason());
        assertEquals(KillSwitchPropagationPolicy.Reason.CONFLICT,
                policy.evaluate(envelope,
                        snapshot(KillSwitchStatus.DISENGAGED, 2, NOW.minusSeconds(1), NOW), NOW).reason());
    }

    @Test
    void shouldDenySendWhenKillEngagesAfterClaim() {
        MutableRepository repository = new MutableRepository(state(KillSwitchStatus.DISENGAGED, 1));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        KillSwitchService service = new KillSwitchService(repository, clock);
        WorkerOperationSafetyGate gate = new WorkerOperationSafetyGate(service, policy, clock);
        KillSwitchPropagationEnvelope claimEnvelope = KillSwitchPropagationEnvelope.fromSnapshot(service.snapshot());

        assertEquals(WorkerOperationSafetyGate.Status.ALLOWED,
                gate.authorize(WorkerOperationSafetyGate.Phase.CLAIM, claimEnvelope).status());

        repository.current = state(KillSwitchStatus.ENGAGED, 2);

        WorkerOperationSafetyGate.Decision send =
                gate.authorize(WorkerOperationSafetyGate.Phase.SEND, claimEnvelope);
        assertEquals(WorkerOperationSafetyGate.Status.DENIED, send.status());
        assertEquals(KillSwitchPropagationPolicy.Reason.ENGAGED, send.reason());
    }

    private static KillSwitchSnapshot snapshot(
            KillSwitchStatus status, long version, Instant updatedAt, Instant observedAt) {
        return new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING, status, version, "TEST", "DURABLE_STORE",
                updatedAt, observedAt, "trace-kill");
    }

    private static KillSwitchSnapshot unknown() {
        return new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING, KillSwitchStatus.UNKNOWN, 0, "READ_FAILED", "DURABLE_STORE",
                null, NOW, "trace-unknown");
    }

    private static KillSwitchState state(KillSwitchStatus status, long version) {
        return new KillSwitchState(
                KillSwitchScope.GLOBAL_TRADING, status, version, "TEST", "DURABLE_STORE",
                NOW.minusSeconds(2), "operator", "trace-kill");
    }

    private static final class MutableRepository implements KillSwitchStateRepository {
        private KillSwitchState current;
        private MutableRepository(KillSwitchState current) { this.current = current; }
        @Override public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            return Optional.ofNullable(current);
        }
        @Override public KillSwitchState engage(KillSwitchEngageCommand command) {
            throw new UnsupportedOperationException("test read-only repository");
        }
    }
}
