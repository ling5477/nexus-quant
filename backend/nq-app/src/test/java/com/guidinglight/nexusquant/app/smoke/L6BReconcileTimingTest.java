package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class L6BReconcileTimingTest {
    @Test void recoverySuccessCannotCrossExistingTotalDeadline() {
        long deadline=20_000_000_000L;
        L6BRuntime.requireRecoveryBeforeDeadline(deadline-1,deadline);
        assertThrows(AssertionError.class,()->L6BRuntime.requireRecoveryBeforeDeadline(deadline,deadline));
        assertThrows(AssertionError.class,()->L6BRuntime.requireRecoveryBeforeDeadline(deadline+45_000_000_000L,deadline));
    }
    @Test void completionOwnsNextDelayAndMissedSlotsAreNotReplayed() {
        var clock = new L6BReconcileTiming();
        clock.begin(0);
        assertThrows(IllegalStateException.class, () -> clock.begin(6_000_000_000L));
        clock.complete(6_000_000_000L);
        assertEquals(11_000_000_000L, clock.next());
        assertThrows(IllegalStateException.class, () -> clock.begin(10_999_999_999L));
        clock.begin(100_000_000_000L);
        clock.complete(102_000_000_000L);
        assertEquals(107_000_000_000L, clock.next());
        assertTrue(B0Processes.Child.RESPONSE_SECONDS > L6CommandExecution.EXECUTION_SECONDS);
        assertEquals(45, L6CommandExecution.EXECUTION_SECONDS);
        assertEquals(75, B0Processes.Child.RESPONSE_SECONDS);
    }

    @Test void oldFiveSecondDeadlineAbortsAndLateResultPermanentlyClosesGeneration() throws Exception {
        var fixture = new ObjectMapper().readTree(
                getClass().getResourceAsStream("/l6b-169m20-timeout.json"));
        assertEquals("UNKNOWN", fixture.path("normalExecutionVersusHang").asText());
        assertEquals("L6_RECONCILE", fixture.path("command").asText());
        assertEquals(L6CommandExecution.EXECUTION_SECONDS, fixture.path("childExecutionBoundSeconds").asLong());
        long oldBound = fixture.path("controllerResponseBoundSeconds").asLong();
        assertEquals(5, oldBound);
        var dir = B0Processes.root().resolve("backend/nq-app/target/l6-timing-protocol/"+UUID.randomUUID());
        try (var child = new B0Processes.Child(L6BTimingChildMain.class,dir,"old-five-second",B0Processes.cleanEnvironment()).awaitReady()) {
            child.startCommand("SLOW");
            assertThrows(AssertionError.class, () -> child.resultBefore(System.nanoTime()+TimeUnit.SECONDS.toNanos(oldBound)));
            Thread.sleep(1500);
            assertTrue(Files.readString(child.log).contains("B0_RESULT SLOW"));
            assertThrows(IllegalStateException.class, () -> child.startCommand("NEXT"));
            assertThrows(IllegalStateException.class, child::result);
            assertFalse(Files.readString(child.log).contains("B0_RESULT NEXT"));
        }
    }

    @Test void boundedSlowSuccessKeepsSequenceAndChildDeathBlocks() throws Exception {
        var dir = B0Processes.root().resolve("backend/nq-app/target/l6-timing-protocol/"+UUID.randomUUID());
        try (var child = new B0Processes.Child(L6BTimingChildMain.class,dir,"bounded",B0Processes.cleanEnvironment()).awaitReady()) {
            long start=System.nanoTime();
            assertEquals("SLOW",child.sendBounded("SLOW"));
            assertTrue(System.nanoTime()-start>TimeUnit.SECONDS.toNanos(5));
            assertTrue(child.process.isAlive());
            assertEquals("NEXT",child.sendBounded("NEXT"));
            assertThrows(AssertionError.class, () -> child.sendBounded("DIE"));
            assertThrows(IllegalStateException.class, () -> child.sendBounded("NEXT"));
        }
    }

    @Test void trueCommandExecutionTimeoutFailsClosedAtAuthoritativeBound() throws Exception {
        var dir = B0Processes.root().resolve("backend/nq-app/target/l6-timing-protocol/"+UUID.randomUUID());
        try (var child = new B0Processes.Child(L6BTimingChildMain.class,dir,"execution-timeout",B0Processes.cleanEnvironment()).awaitReady()) {
            long start=System.nanoTime();
            assertThrows(AssertionError.class, () -> child.sendBounded("HANG"));
            long elapsed=System.nanoTime()-start;
            assertTrue(elapsed>=TimeUnit.SECONDS.toNanos(45) && elapsed<TimeUnit.SECONDS.toNanos(75));
            assertTrue(Files.readString(child.log).contains("TimeoutException"));
            assertThrows(IllegalStateException.class, () -> child.sendBounded("NEXT"));
            assertFalse(Files.readString(child.log).contains("B0_RESULT NEXT"));
        }
    }
}
