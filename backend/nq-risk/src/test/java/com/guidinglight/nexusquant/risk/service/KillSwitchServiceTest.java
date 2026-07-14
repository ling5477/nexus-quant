package com.guidinglight.nexusquant.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class KillSwitchServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void readsEngagedAndDisengagedDurableSnapshots() {
        KillSwitchSnapshot engaged = service(repository(state(KillSwitchStatus.ENGAGED, NOW.minusSeconds(1))))
                .snapshot();
        KillSwitchSnapshot disengaged = service(repository(state(KillSwitchStatus.DISENGAGED, NOW.minusSeconds(1))))
                .snapshot();

        assertEquals(KillSwitchStatus.ENGAGED, engaged.status());
        assertTrue(engaged.blocksOperations());
        assertEquals(KillSwitchStatus.DISENGAGED, disengaged.status());
        assertFalse(disengaged.blocksOperations());
        assertEquals(NOW, disengaged.observedAt());
    }

    @Test
    void missingRepositoryFailureAndFutureTimestampAreUnknownAndBlocked() {
        KillSwitchSnapshot missing = service(repository(null)).snapshot();
        KillSwitchSnapshot failure = service(new FailingRepository()).snapshot();
        KillSwitchSnapshot invalidStatus = service(new MalformedRepository("invalid persisted status"))
                .snapshot();
        KillSwitchSnapshot missingTimestamp = service(new MalformedRepository("missing persisted timestamp"))
                .snapshot();
        KillSwitchSnapshot future = service(repository(state(KillSwitchStatus.DISENGAGED, NOW.plusSeconds(1))))
                .snapshot();

        assertUnknown(missing, "KILL_SWITCH_STATE_MISSING");
        assertUnknown(failure, "KILL_SWITCH_STATE_READ_FAILED");
        assertUnknown(invalidStatus, "KILL_SWITCH_STATE_READ_FAILED");
        assertUnknown(missingTimestamp, "KILL_SWITCH_STATE_READ_FAILED");
        assertUnknown(future, "KILL_SWITCH_UPDATED_AT_FUTURE");
    }

    @Test
    void engageUsesInjectedClockAndHasNoProductionReleaseSurface() {
        RecordingRepository repository = new RecordingRepository(state(KillSwitchStatus.DISENGAGED, NOW.minusSeconds(1)));
        KillSwitchSnapshot result = service(repository).engage(7, "MANUAL_SAFETY_STOP", "operator-1", "trace-1");

        assertEquals(KillSwitchStatus.ENGAGED, result.status());
        assertEquals(NOW, result.updatedAt());
        assertEquals(8, result.version());
        assertEquals(NOW, repository.command.occurredAt());
        assertEquals("OPERATOR_ENGAGE", repository.command.source());

        Set<String> publicMethods = Arrays.stream(KillSwitchService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("snapshot", "engage"), publicMethods);
        assertFalse(publicMethods.stream().anyMatch(name -> Set.of(
                "disable", "disengage", "release", "reset", "clear"
        ).contains(name)));
    }

    @Test
    void stateAndSnapshotRejectInvalidPersistedValues() {
        assertTrue(KillSwitchSnapshot.class.isRecord());
        assertTrue(Modifier.isFinal(KillSwitchSnapshot.class.getModifiers()));
        assertThrows(IllegalArgumentException.class, () -> new KillSwitchState(
                KillSwitchScope.GLOBAL_TRADING,
                KillSwitchStatus.UNKNOWN,
                1,
                "UNKNOWN",
                "TEST",
                NOW,
                "tester",
                "trace"
        ));
        assertThrows(IllegalArgumentException.class, () -> new KillSwitchState(
                KillSwitchScope.GLOBAL_TRADING,
                KillSwitchStatus.ENGAGED,
                1,
                "VALID",
                "TEST",
                null,
                "tester",
                "trace"
        ));
    }

    private static void assertUnknown(KillSwitchSnapshot snapshot, String reasonCode) {
        assertEquals(KillSwitchStatus.UNKNOWN, snapshot.status());
        assertEquals(0, snapshot.version());
        assertEquals(reasonCode, snapshot.reasonCode());
        assertTrue(snapshot.blocksOperations());
    }

    private static KillSwitchService service(KillSwitchStateRepository repository) {
        return new KillSwitchService(repository, CLOCK);
    }

    private static KillSwitchStateRepository repository(KillSwitchState state) {
        return new RecordingRepository(state);
    }

    private static KillSwitchState state(KillSwitchStatus status, Instant updatedAt) {
        return new KillSwitchState(
                KillSwitchScope.GLOBAL_TRADING,
                status,
                7,
                "TEST_STATE",
                "TEST_FIXTURE",
                updatedAt,
                "tester",
                "trace-state"
        );
    }

    private static final class RecordingRepository implements KillSwitchStateRepository {
        private KillSwitchState state;
        private KillSwitchEngageCommand command;

        private RecordingRepository(KillSwitchState state) {
            this.state = state;
        }

        @Override
        public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            return Optional.ofNullable(state);
        }

        @Override
        public KillSwitchState engage(KillSwitchEngageCommand command) {
            this.command = command;
            if (state == null || state.version() != command.expectedVersion()) {
                throw new KillSwitchVersionConflictException("test version conflict");
            }
            if (state.status() == KillSwitchStatus.ENGAGED) {
                return state;
            }
            state = new KillSwitchState(
                    command.scope(),
                    KillSwitchStatus.ENGAGED,
                    state.version() + 1,
                    command.reasonCode(),
                    command.source(),
                    command.occurredAt(),
                    command.updatedBy(),
                    command.traceId()
            );
            return state;
        }
    }

    private static final class FailingRepository implements KillSwitchStateRepository {
        @Override
        public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            throw new IllegalStateException("simulated store failure");
        }

        @Override
        public KillSwitchState engage(KillSwitchEngageCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    /** 模拟 JDBC mapper 遇到非法 status 或缺失 timestamp 时的解析失败。 */
    private static final class MalformedRepository implements KillSwitchStateRepository {
        private final String message;

        private MalformedRepository(String message) {
            this.message = message;
        }

        @Override
        public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
            throw new IllegalArgumentException(message);
        }

        @Override
        public KillSwitchState engage(KillSwitchEngageCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
