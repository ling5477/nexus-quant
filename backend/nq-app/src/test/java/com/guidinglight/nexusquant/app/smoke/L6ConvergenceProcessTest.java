package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 两次回读间由独立 JVM 提交赢家；控制器只读业务事实，失败前亦保留数据库现场。 */
@EnabledIfSystemProperty(named = "nq.l6.convergence", matches = "true")
class L6ConvergenceProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean baseline = Boolean.getBoolean("nq.l6.convergence.baseline");

    @Test void sameTargetAndScheduler() throws Exception {
        try (var pg = B0Processes.Pg.start()) {
            race(pg, 2, false, "FILLED");
            race(pg, 2, true, "FILLED");
            if (!baseline) {
                race(pg, 4, false, "FILLED");
                race(pg, 2, false, "PARTIALLY_FILLED");
                missingLedger(pg);
            }
        }
    }

    private void race(B0Processes.Pg pg, int actors, boolean scheduled, String target) throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/l6-convergence/"
                + (baseline ? "before-" : "after-") + actors + "-" + scheduled + "-" + target + "-" + UUID.randomUUID());
        Files.createDirectories(dir);
        Path legacy = baseline ? L6ConvergenceBaseline.compile(dir.resolve("baseline")) : null;
        var proof = mapper.createObjectNode().put("baseline", baseline).put("actors", actors).put("scheduled", scheduled);
        var children = new ArrayList<B0Processes.Child>();
        try (var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env);
            try (var reader = fixture.checker()) {
                try {
                    proof.put("postgres", value(reader, "SHOW server_version"));
                    assertTrue(proof.path("postgres").asText().startsWith("16."));
                    assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
                    for (int i = 0; i < actors; i++) {
                        var child = new B0Processes.Child(L6ConvergenceNqMain.class, dir, "actor-" + i, env, legacy);
                        children.add(child); child.ready();
                        proof.withArray("pids").add(child.process.pid());
                    }
                    var winner = children.getLast();
                    assertTrue(winner.send("PLACE_B2").endsWith("ACCEPTED"));
                    control(endpoint, "FILL " + ("FILLED".equals(target) ? "10" : "4") + " 0");
                    long version = Long.parseLong(value(reader, "SELECT version FROM orders"));
                    for (int i = 0; i < actors - 1; i++) {
                        var child = children.get(i);
                        assertEquals("ARMED " + target, child.send("L6C_ARM " + target));
                        assertEquals(scheduled ? "SCHEDULED" : "BEGIN", child.send(scheduled ? "L6C_SCHEDULE" : "L6C_BEGIN"));
                        awaitCut(child, "L6C_CUT " + target);
                    }
                    assertEquals("RECOVER 1", winner.send("RECOVER"));
                    assertEquals(target, value(reader, "SELECT status FROM orders"));
                    assertEquals(version + 1, Long.parseLong(value(reader, "SELECT version FROM orders")));
                    proof.set("winner", snapshot(reader));
                    for (int i = 0; i < actors - 1; i++) {
                        var child = children.get(i);
                        assertEquals("RELEASED", child.send("L6C_RELEASE"));
                        if (scheduled) {
                            String ticks = "";
                            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                            do {
                                ticks = child.send("L6C_TICKS");
                                if (Integer.parseInt(ticks.split(" ")[1]) >= 3) break;
                                Thread.sleep(100);
                            } while (System.nanoTime() < deadline);
                            assertTrue(Integer.parseInt(ticks.split(" ")[1]) >= 3, ticks);
                            assertTrue(ticks.endsWith("ERRORS " + (baseline ? 1 : 0)), ticks);
                            proof.put("scheduler", ticks);
                            child.send("L6C_UNSCHEDULE");
                        } else {
                            String outcome = child.send("L6C_AWAIT");
                            proof.withArray("outcomes").add(outcome);
                            assertEquals(baseline ? "ERROR invalid order transition: FILLED -> FILLED" : "OK", outcome);
                        }
                        assertTrue(child.process.isAlive());
                        assertEquals("RECOVER 0", child.send("RECOVER"));
                    }
                    for (int i = 0; i < 3; i++) assertEquals("RECOVER 0", winner.send("RECOVER"));
                    assertEquals(proof.path("winner"), snapshot(reader));
                    assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                    assertEquals("2", value(reader, "SELECT count(*) FROM ledger_entries"));
                    assertAccounting(reader, "FILLED".equals(target) ? "10" : "4");
                    proof.put("result", "PASS");
                } finally {
                    proof.set("finalDb", snapshot(reader));
                    Files.writeString(dir.resolve("proof.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
                    B0Processes.closeChildren(children);
                }
            }
        }
        System.out.println("L6C_PROOF " + dir);
    }

    private void missingLedger(B0Processes.Pg pg) throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/l6-convergence/missing-" + UUID.randomUUID());
        Files.createDirectories(dir);
        try (var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env);
            try (var reader = fixture.checker(); var child = new B0Processes.Child(L6ConvergenceNqMain.class, dir, "crash", env)) {
                child.ready(); child.send("PLACE_B2"); control(endpoint, "FILL 10 0");
                child.send("ARM_B4_TRADE_COMMIT"); child.startCommand("RECOVER");
                awaitCut(child, "B4_CUT AFTER_TRADE_COMMIT");
                assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries"));
                var proof = mapper.createObjectNode(); proof.set("atCrash", snapshot(reader));
                String version = value(reader, "SELECT version FROM orders");
                child.kill();
                try (var restart = new B0Processes.Child(L6ConvergenceNqMain.class, dir, "restart", env)) {
                    restart.ready(); assertEquals("RECOVER 0", restart.send("RECOVER"));
                    assertEquals(version, value(reader, "SELECT version FROM orders"));
                    assertEquals("2", value(reader, "SELECT count(*) FROM ledger_entries"));
                    assertAccounting(reader, "10");
                    proof.set("recovered", snapshot(reader));
                    assertEquals("RECOVER 0", restart.send("RECOVER"));
                    assertEquals(proof.path("recovered"), snapshot(reader));
                    proof.put("result", "PASS");
                    Files.writeString(dir.resolve("proof.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
                }
            }
        }
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        var result = new B2RealProcessProofTest().snapshot(reader);
        result.put("events", value(reader, "SELECT count(*) FROM event_store"));
        result.put("transitions", value(reader, "SELECT count(*) FROM audit_logs WHERE action='ORDER_STATUS_TRANSITION'"));
        return result;
    }

    private void assertAccounting(Connection reader, String qty) throws Exception {
        assertEquals(0, new BigDecimal(qty).compareTo(new BigDecimal(value(reader, "SELECT qty FROM positions"))));
        assertEquals(0, new BigDecimal(qty).compareTo(new BigDecimal(value(reader,
                "SELECT balance FROM account_snapshots WHERE currency='BTC' ORDER BY snapshot_id DESC LIMIT 1"))));
        assertEquals("1", value(reader, "SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'"));
        assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries l LEFT JOIN trades t ON t.trade_id=l.ref_id WHERE t.trade_id IS NULL"));
    }

    private String value(Connection reader, String sql) throws Exception {
        try (var statement = reader.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getString(1); }
    }

    private void control(String endpoint, String command) throws Exception {
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            var result = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, result.statusCode(), result.body());
        }
    }

    private void awaitCut(B0Processes.Child child, String marker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            assertTrue(child.process.isAlive());
            if (Files.readString(child.log).contains(marker)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("missing cut: " + marker);
    }
}
