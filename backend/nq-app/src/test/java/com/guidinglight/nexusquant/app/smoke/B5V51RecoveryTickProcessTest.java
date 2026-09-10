package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 没有 scan/recover 请求时由生产启动及 tick 接手 disabled manual 工作。 */
@EnabledIfSystemProperty(named = "nq.b5.v51", matches = "true")
class B5V51RecoveryTickProcessTest {
    @Test void disabledManualRunUsesImmutableOverrideAtStartupAndTerminalTick() throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/b5-v51-tick/" + UUID.randomUUID()); Files.createDirectories(dir);
        var mapper = new ObjectMapper(); var proof = mapper.createObjectNode().put("scenario", "DISABLED_MANUAL_STARTUP_TICK");
        System.out.println("B5_V51_TICK_ROOT " + dir);
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment(); env.put("NQ_B0_DB", fixture.url());
            env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env); B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var reader = fixture.checker(); var a = new B0Processes.Child(B0NqProcessMain.class, dir, "a", env).awaitReady()) {
                a.send("ARM_B5_ADMISSION"); a.send("BEGIN_V51_MANUAL");
                long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                while (!Files.readString(a.log).contains("B5_CUT AFTER_ADMISSION")) { assertTrue(System.nanoTime() < deadline); Thread.sleep(20); }
                String runId = value(reader, "SELECT strategy_run_id FROM strategy_runs");
                a.send("DISABLE_V51"); a.kill();
                assertEquals("CREATED", value(reader, "SELECT status FROM strategy_runs"));
                try (var b = new B0Processes.Child(B5V51NqRecoveryMain.class, dir, "b", env).awaitReady()) {
                    awaitStatus(reader, "RUNNING");
                    assertEquals("7.00000000", value(reader, "SELECT qty FROM orders"));
                    assertEquals("f", value(reader, "SELECT enabled FROM strategy_definitions"));
                    assertEquals("f", value(reader, "SELECT enabled FROM strategy_schedules"));
                    try (var client = HttpClient.newHttpClient()) {
                        assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                                .POST(HttpRequest.BodyPublishers.ofString("FILL 7 0.01")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                        b.send("RECOVER");
                        awaitStatus(reader, "SUCCEEDED");
                        var facts = mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts")).timeout(Duration.ofSeconds(5))
                                .GET().build(), HttpResponse.BodyHandlers.ofString()).body());
                        assertEquals(1, facts.path("placeRequests").asInt()); proof.set("venue", facts);
                    }
                    assertEquals(runId, value(reader, "SELECT strategy_run_id FROM strategy_runs"));
                    assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                    proof.put("database", fixture.name()).put("nqPid", a.process.pid()).put("restartPid", b.process.pid())
                            .put("venuePid", venue.process.pid()).put("strategyRunId", runId).put("quantity", "7.00000000")
                            .put("result", "PASS");
                }
            }
        } catch (Exception | AssertionError failure) { proof.put("result", "FAIL"); throw failure; }
        finally {
            Path raw = dir.resolve("raw-proof.json"); Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
            SyntheticEvidenceExport.write(raw, "B5-V51T", 1);
        }
    }
    private static String value(Connection c, String sql) throws Exception { return B5StrategyRunRecoveryProcessTest.value(c, sql); }
    private static void awaitStatus(Connection c, String status) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!status.equals(value(c, "SELECT status FROM strategy_runs"))) { assertTrue(System.nanoTime() < deadline, "independent recovery tick did not progress " + status); Thread.sleep(30); }
    }
}
