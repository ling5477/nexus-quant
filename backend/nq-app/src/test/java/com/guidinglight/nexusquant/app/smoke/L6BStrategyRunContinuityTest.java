package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.facts;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.http;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.number;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.sample;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 重建历史675恢复后679尚未投影的切点；行锁只延迟投影，不伪造任何业务状态。 */
@EnabledIfSystemProperty(named = "nq.l6b.strategyRegression", matches = "true")
class L6BStrategyRunContinuityTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final long diagnosticStart = System.nanoTime();
    private final List<L6DeterministicPacer.Slot> successful = new ArrayList<>();

    @Test void restartedGenerationWaitsForDurableProjectionAndResumes() throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/l6-b-strategy-regression/" + UUID.randomUUID());
        Files.createDirectories(dir);
        System.out.println("L6_B_STRATEGY_REGRESSION_ROOT " + dir);
        var contract = new L6BContract(true);
        var parameters = contract.identity().put("runOrderBudget", contract.orders).put("formalTimerStarted", false)
                .put("diagnosticOnly", true).put("strategyRecoveryDiagnostic", true);
        parameters.set("manifestEntry", contract.manifest.identity());
        Files.writeString(dir.resolve("parameters.json"), parameters.toString());
        var proof = JSON.createObjectNode().put("scope", "REMEDIATION_REGRESSION_NOT_QUALIFICATION")
                .put("baselineHead", "b19445d47e1131ff0a5f4ce2a6cc807a7bdb05e9");
        try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(L6BVenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            L6PgBaselinePreflight.initialize(fixture, endpoint, env);
            try (var reader = fixture.checker();
                 var old = new B0Processes.Child(L6NqProcessMain.class, dir, "nq-old", env).awaitReady()) {
                http(endpoint, "L5_OPEN");
                String identity = value(reader, "SELECT identity FROM b0_fixture_identity");
                String postmaster = value(reader, "SELECT pg_postmaster_start_time()::text");
                proof.set("slot675", emit(old, 675));
                var legitimateBusy = emit(old, 677);
                assertEquals("SKIPPED_BUSY", legitimateBusy.path("outcome").asText());
                assertTrue(old.process.isAlive()); assertEquals(1, number(reader, "SELECT count(*) FROM strategy_runs"));
                proof.set("legitimateBusy", legitimateBusy);
                var unresolved = new L6BAdmission(row -> proof.withArray("unresolvedBarrier").add(row));
                long waiting = System.nanoTime();
                var timeout = assertThrows(IllegalStateException.class, () -> {
                    while (true) {
                        assertTrue(unresolved.observe(reader, sample(reader), System.nanoTime() - waiting));
                        TimeUnit.MILLISECONDS.sleep(100);
                    }
                });
                assertEquals("RESTART_CONTINUITY_NOT_CONVERGED", timeout.getMessage());
                assertEquals(1, number(reader, "SELECT count(*) FROM orders"));
                assertEquals(1, number(reader, "SELECT count(*) FROM strategy_runs WHERE status IN ('CREATED','DISPATCHING','RUNNING')"));
                proof.put("unresolvedFailureClosed", true);
                proof.set("beforeRestart", facts(reader));
                long oldPid = old.process.pid(); old.kill();
                try (var next = new B0Processes.Child(L6NqProcessMain.class, dir, "nq-new", env).awaitReady()) {
                    assertFalse(old.process.isAlive()); assertNotEquals(oldPid, next.process.pid());
                    proof.put("oldPid", oldPid).put("newPid", next.process.pid());
                    http(endpoint, "FILL"); next.send("L6_RECONCILE");
                    awaitSucceeded(reader, 1);
                    proof.set("oldRunRecovered", facts(reader));
                    var emitted = emit(next, 679); proof.set("slot679", emitted);
                    String runId = emitted.path("strategyRunId").asText();
                    // NO KEY UPDATE阻止恢复投影，但允许Order/Trade FK读取与真实成交入账。
                    try (var lock = DriverManager.getConnection(fixture.url(), "postgres", "")) {
                        lock.setAutoCommit(false);
                        try (var statement = lock.prepareStatement("SELECT strategy_run_id FROM strategy_runs WHERE strategy_run_id=? FOR NO KEY UPDATE")) {
                            statement.setString(1, runId); statement.executeQuery().close();
                        }
                        long lockedAt = System.nanoTime();
                        http(endpoint, "FILL"); next.send("L6_RECONCILE");
                        assertEquals(0, sample(reader).path("backlog").asLong());
                        assertEquals(2, number(reader, "SELECT count(*) FROM trades"));
                        assertEquals(8, number(reader, "SELECT count(*) FROM ledger_entries"));
                        assertEquals(1, number(reader, "SELECT count(*) FROM strategy_runs WHERE status IN ('CREATED','DISPATCHING','RUNNING')"));
                        proof.set("terminalOrderActiveRun", facts(reader));
                        var barrier = new L6BAdmission(row -> proof.withArray("admissionBarrier").add(row));
                        assertTrue(barrier.observe(reader, sample(reader), System.nanoTime() - diagnosticStart));
                        var busy = emit(next, 681);
                        proof.set("postRestartBusy", busy);
                        assertEquals("SKIPPED_BUSY", busy.path("outcome").asText());
                        assertTrue(busy.path("admissionRolledBack").asBoolean());
                        assertTrue(next.process.isAlive());
                        assertEquals(2, number(reader, "SELECT count(*) FROM strategy_runs"));
                        assertEquals(2, number(reader, "SELECT count(*) FROM orders"));
                        proof.put("lockHeldMillis", (System.nanoTime() - lockedAt) / 1_000_000);
                        lock.rollback();
                    }
                    awaitSucceeded(reader, 2);
                    proof.set("projectedAfterRelease", facts(reader));
                    var ready = new L6BAdmission(row -> proof.withArray("readyBarrier").add(row));
                    assertFalse(ready.observe(reader, sample(reader), System.nanoTime() - diagnosticStart));
                    proof.set("slot683", emit(next, 683));
                    http(endpoint, "FILL"); next.send("L6_RECONCILE"); awaitSucceeded(reader, 3);
                    for (int i = 0; i < 3; i++) { next.send("L6_OBSERVER_SCAN"); next.send("L6_RECONCILE"); }
                    reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); reader.setAutoCommit(false);
                    proof.set("final", L6BCheckpoint.verify(reader, endpoint, List.of(next), dir, "REGRESSION", 1,
                            false, contract.orders, successful, L6BContract.MODE, () -> false));
                    reader.commit(); reader.setAutoCommit(true);
                    assertEquals(identity, value(reader, "SELECT identity FROM b0_fixture_identity"));
                    assertEquals(postmaster, value(reader, "SELECT pg_postmaster_start_time()::text"));
                    proof.put("samePg", true).put("busyProtectionPreserved", true)
                            .put("result", "REMEDIATED_CONTINUITY_REGRESSION_PASS");
                }
            }
        } finally {
            Files.writeString(dir.resolve("proof.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
    }

    private JsonNode emit(B0Processes.Child child, int slot) throws Exception {
        long clock = Long.parseLong(child.send("L6_CLOCK"));
        long begin = System.nanoTime() - diagnosticStart;
        var result = JSON.readTree(child.send("L6_EMIT " + slot + " " + (clock + 60_000_000_000L)));
        if (result.hasNonNull("logicalOrderId")) successful.add(new L6DeterministicPacer.Slot(slot, begin, begin,
                "EMITTED", 0, result.path("logicalOrderId").asText(), System.nanoTime() - diagnosticStart, "DIAGNOSTIC_NOT_FORMAL_PACING"));
        return result;
    }

    private static void awaitSucceeded(Connection reader, int count) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (number(reader, "SELECT count(*) FROM strategy_runs WHERE status='SUCCEEDED'") != count) {
            assertTrue(System.nanoTime() < deadline, "durable recovery must converge within diagnostic bound");
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }
}
