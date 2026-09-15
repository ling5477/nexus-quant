package com.guidinglight.nexusquant.app.smoke;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 控制权拒绝发生在任何stop动作之前；真实子进程的存活与正常退出提供正反证据。 */
class L6BOwnershipTest {
    @TempDir Path directory;
    @Test void wrongPidNonOwnedPidAndAmbiguousGenerationCannotBeKilled() throws Exception {
        try(var child=new B0Processes.Child(L6BOwnershipChildMain.class,directory,"owned",B0Processes.cleanEnvironment()).awaitReady();
            var outsider=new B0Processes.Child(L6BOwnershipChildMain.class,directory,"outsider",B0Processes.cleanEnvironment()).awaitReady();
            var registry=new L6BActors("container","database","pg-start",ProcessHandle.current())) {
            var generation=new L6BActors.Generation(0,0,child,"INITIAL");registry.enroll(generation);generation.state="RUNNING";
            assertThrows(IllegalStateException.class,()->registry.stop(0,outsider.process.pid(),true));
            assertThrows(IllegalStateException.class,()->registry.stop(0,ProcessHandle.current().pid(),true));
            assertTrue(child.process.isAlive());assertTrue(outsider.process.isAlive());
            assertThrows(IllegalStateException.class,()->registry.enroll(new L6BActors.Generation(0,0,outsider,"AMBIGUOUS")));
            assertThrows(IllegalStateException.class,()->registry.enroll(new L6BActors.Generation(1,0,child,"DUPLICATE_PID")));
            assertEquals(1,registry.evidence().size());
            registry.stop(0,child.process.pid(),false);
            assertFalse(child.process.isAlive());assertTrue(outsider.process.isAlive());
            assertEquals("DOWN",registry.get(0).state);assertNotNull(registry.get(0).stoppedAt);
        }
    }
    @Test void replacedPgDatabaseOrVenueIdentityIsRejected() throws Exception {
        try(var registry=new L6BActors("container","database","pg-start",ProcessHandle.current())) {
            long venue=ProcessHandle.current().pid();
            registry.continuity("container","database","pg-start",venue);
            assertThrows(IllegalStateException.class,()->registry.continuity("replacement","database","pg-start",venue));
            assertThrows(IllegalStateException.class,()->registry.continuity("container","replacement","pg-start",venue));
            assertThrows(IllegalStateException.class,()->registry.continuity("container","database","new-pg-start",venue));
            assertThrows(IllegalStateException.class,()->registry.continuity("container","database","pg-start",venue+1));
        }
    }
    @Test void readinessWithoutBusinessRecoveryIsRejected() {
        var ready=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("ready",true);
        assertThrows(AssertionError.class,()->L6BRuntime.requireRecovered(true,ready));
        ready.put("schedulerResumed",true).put("reconciliationResumed",true);
        assertThrows(AssertionError.class,()->L6BRuntime.requireRecovered(true,ready));
        ready.putObject("recoveredFullChain").putObject("oracle").put("orders",1);
        L6BRuntime.requireRecovered(true,ready);
        assertThrows(AssertionError.class,()->L6BRuntime.requireRecovered(false,ready));
    }
}
