package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 复用真实四进程驱动，在精确目标处退出；这些短运行不计正式资格 repetition。 */
@EnabledIfSystemProperty(named = "nq.l5.targetFixtures", matches = "true")
class L5TargetCleanupTest {
    @Test void releasedTarget() throws Exception { execute("RELEASE"); }
    @Test void assertionAtTarget() throws Exception { execute("ASSERTION"); }
    @Test void exceptionAtTarget() throws Exception { execute("EXCEPTION"); }
    @Test void missingTargetTimesOutWithoutFaultKill() throws Exception { execute("TIMEOUT"); }

    private void execute(String outcome) throws Exception {
        String previous = System.getProperty("nq.l5.targetOutcome");
        var dir = Files.createTempDirectory(B0Processes.root().resolve("backend/nq-app/target/l5-targeting"), outcome + "-");
        try {
            System.setProperty("nq.l5.targetOutcome", outcome);
            Class<? extends Throwable> expected = outcome.equals("ASSERTION") || outcome.equals("TIMEOUT")
                    ? AssertionError.class : IllegalStateException.class;
            Throwable failure = assertThrows(expected, () -> new L5BoundedWorkloadTest().execute(dir, 3));
            assertTrue(failure.getMessage().contains(outcome.equals("TIMEOUT") ? "FAULT_TARGET_NOT_REACHED" : "EXPECTED_TARGET_" + outcome));
            var proof = new ObjectMapper().readTree(dir.resolve("raw-proof.json").toFile());
            var cleanup = proof.path("concurrent").path("targetCleanup");
            assertEquals(0, cleanup.path("ownedNq").asInt(-1));
            assertEquals(0, cleanup.path("ownedVenue").asInt(-1));
            assertEquals(0, cleanup.path("ownedPg").asInt(-1));
            var fault = proof.path("concurrent").path("fault");
            assertEquals(0, fault.path("faultKills").asInt(-1));
            assertEquals(!outcome.equals("TIMEOUT"), fault.path("released").asBoolean());
            System.out.println("L5_TARGET_FIXTURE_PASS " + outcome + " " + dir);
        } finally {
            if (previous == null) System.clearProperty("nq.l5.targetOutcome");
            else System.setProperty("nq.l5.targetOutcome", previous);
        }
    }
}
