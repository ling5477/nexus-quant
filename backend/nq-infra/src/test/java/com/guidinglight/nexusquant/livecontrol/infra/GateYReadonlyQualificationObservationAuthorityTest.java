package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.risk.service.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchState;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GateYReadonlyQualificationObservationAuthorityTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    @Test
    void engagedKillSwitchAllowsOnlyTheTrustedDelegatePath() {
        AtomicInteger delegateCalls = new AtomicInteger();
        PilotPrerequisiteObservationAuthority delegate = (session, scope, resolvedAt) -> {
            delegateCalls.incrementAndGet();
            return null;
        };
        var authority = new KillSwitchGuardedProviderObservationAuthority(
                delegate, killSwitch(KillSwitchStatus.ENGAGED));

        PilotObservationSet result = authority.resolveTrustedObservationSet(null, null, NOW);

        assertNull(result);
        assertEquals(1, delegateCalls.get());
    }

    @Test
    void nonEngagedOrUnknownKillSwitchStopsBeforeCredentialDelegate() {
        for (KillSwitchStatus status : new KillSwitchStatus[]{
                KillSwitchStatus.DISENGAGED, KillSwitchStatus.UNKNOWN}) {
            AtomicInteger delegateCalls = new AtomicInteger();
            var authority = new KillSwitchGuardedProviderObservationAuthority(
                    (session, scope, resolvedAt) -> {
                        delegateCalls.incrementAndGet();
                        return null;
                    },
                    killSwitch(status)
            );

            LiveControlException failure = assertThrows(
                    LiveControlException.class,
                    () -> authority.resolveTrustedObservationSet(null, null, NOW)
            );

            assertEquals("READ_ONLY_PROVIDER_OBSERVATION_KILL_SWITCH_REQUIRED", failure.code());
            assertEquals(0, delegateCalls.get());
        }
    }

    private static KillSwitchService killSwitch(KillSwitchStatus status) {
        KillSwitchStateRepository repository = new KillSwitchStateRepository() {
            @Override
            public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
                return Optional.of(new KillSwitchState(
                        scope, status, 1, "TEST_STATE", "TEST_FIXTURE",
                        NOW.minusSeconds(1), "tester", "trace-gatey-readonly"));
            }

            @Override
            public KillSwitchState engage(KillSwitchEngageCommand command) {
                throw new UnsupportedOperationException();
            }
        };
        return new KillSwitchService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
