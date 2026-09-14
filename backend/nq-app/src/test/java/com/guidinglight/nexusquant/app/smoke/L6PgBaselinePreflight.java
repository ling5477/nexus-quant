package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.guidinglight.nexusquant.app.smoke.L5BoundedWorkloadTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** 有界无订单准备与正式入口共用bootstrap；准备实例清理后才启动容量冻结的正式PG。 */
final class L6PgBaselinePreflight {
    static void initialize(B0Fixture fixture, String endpoint, Map<String, String> env) throws Exception {
        fixture.initialize(true, endpoint, env); L6ActiveStabilityTest.seed(fixture);
        try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var statement = owner.createStatement()) {
            assertEquals(2, statement.executeUpdate("UPDATE strategy_schedules SET enabled=false WHERE schedule_job_id IN ('l6-schedule-1','l6-schedule-2')"));
        }
    }

    static L6PgRunCapacity measure(L6PgCapacityContract model, Path dir) throws Exception {
        Files.createDirectories(dir);
        var json = new ObjectMapper();
        var proof = json.createObjectNode().put("mode", "BASELINE_PREPARATION_NO_WORKLOAD").put("formalTimerStarted", false);
        // 子进程复用正式fixture协议，但准备阶段没有producer和qualification计时器。
        var parameters = json.createObjectNode().put("mode", "FORMAL_L6_A").put("shortSmoke", false).put("baselinePreparation", true);
        parameters.putObject("manifestEntry").put("sha256", model.manifestSha());
        Files.writeString(dir.resolve("parameters.json"), parameters.toString());
        var actors = new ArrayList<B0Processes.Child>();
        String container = null; long venuePid = 0; L6PgRunCapacity result = null;
        try {
            // 准备阶段使用既有256MiB有界fixture；仍以完整组件预算做60%检查。
            proof.set("hostMemoryPreflight", L6HostMemoryPreflight.verify(model, B0Processes.Pg.defaultTmpfsBytes(), L6HostMemoryPreflight.observe()));
            try (var pg = B0Processes.Pg.startBounded(); var fixture = B0Fixture.create(pg);
                 var venue = new B0Processes.Child(L6FormalVenueProcessMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
                container = pg.ownedContainerId(); venuePid = venue.process.pid();
                proof.put("container", container).put("venuePid", venuePid);
                String endpoint = "http://127.0.0.1:" + venue.ready();
                var env = B0Processes.cleanEnvironment();
                env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
                initialize(fixture, endpoint, env);
                try {
                    for (int i = 0; i < 2; i++) actors.add(new B0Processes.Child(L6NqProcessMain.class, dir, "nq-" + i, env).awaitReady());
                    for (var actor : actors) actor.send("L6_CLOCK");
                    proof.set("paper", json.readTree(actors.getFirst().send("L6_PAPER"))); http(endpoint, "L5_OPEN");
                    try (var reader = fixture.checker()) {
                        assertEquals(0, number(reader, "SELECT count(*) FROM orders"));
                        var measured = L6PgStorageObservation.collect(reader, container, B0Processes::command);
                        proof.set("baselineMeasurement", measured);
                        result = L6PgRunCapacity.derive(model, measured.path("pgTmpfsUsedBytes").asLong());
                        proof.set("derivedCapacity", result.evidence(model));
                    }
                } finally { B0Processes.closeChildren(actors); }
            }
            assertFalse(ProcessHandle.of(venuePid).map(ProcessHandle::isAlive).orElse(false));
            assertTrue(B0Processes.command("docker", "ps", "-a", "--filter", "id=" + container, "--format", "{{.ID}}").isBlank());
            proof.put("cleanup", "PASS").put("ownedSurvivors", 0).put("result", "PASS");
            return result;
        } catch (Exception | AssertionError failure) {
            proof.put("result", "FAILED").put("failure", failure.toString()); throw failure;
        } finally {
            Files.writeString(dir.resolve("proof.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        }
    }
}
