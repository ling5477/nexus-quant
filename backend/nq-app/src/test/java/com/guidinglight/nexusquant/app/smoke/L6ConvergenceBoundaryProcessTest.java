package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 相邻状态、真实冲突、CAS 旧观察与同 batch 后续候选的隔离进程证明。 */
@EnabledIfSystemProperty(named = "nq.l6.convergence", matches = "true")
class L6ConvergenceBoundaryProcessTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void preservesBoundaries() throws Exception {
        try (var pg = B0Processes.Pg.start()) {
            for (String scenario : List.of("INVENTORY", "CANCELLED", "REJECTED", "CANCEL_RACE", "STALE_CAS", "CONFLICT", "BATCH")) {
                scenario(pg, scenario);
            }
        }
    }

    private void scenario(B0Processes.Pg pg, String scenario) throws Exception {
        Path dir = B0Processes.root().resolve("backend/nq-app/target/l6-convergence/" + scenario + "-" + UUID.randomUUID());
        Files.createDirectories(dir);
        var proof = mapper.createObjectNode().put("scenario", scenario);
        try (var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child("BATCH".equals(scenario) ? B0SyntheticVenueMain.class : B2SyntheticVenueMain.class,
                     dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env);
            try (var reader = fixture.checker();
                 var a = new B0Processes.Child(L6ConvergenceNqMain.class, dir, "a", env).awaitReady();
                 var b = new B0Processes.Child(L6ConvergenceNqMain.class, dir, "b", env).awaitReady()) {
                a.ready(); b.ready(); proof.put("aPid", a.process.pid()).put("bPid", b.process.pid());
                try {
                    if ("BATCH".equals(scenario)) control(endpoint, "L5_OPEN");
                    assertTrue(b.send("PLACE_B2").endsWith("ACCEPTED"));
                    if (List.of("INVENTORY", "CANCELLED", "REJECTED").contains(scenario)) {
                        List<String> states = switch (scenario) {
                            case "INVENTORY" -> List.of("ACCEPTED", "PARTIALLY_FILLED", "CANCEL_REQUESTED", "CANCEL_REJECTED", "FILLED");
                            case "CANCELLED" -> List.of("ACCEPTED", "CANCEL_REQUESTED", "CANCELLED");
                            default -> List.of("ACCEPTED", "REJECTED");
                        };
                        for (String state : states) {
                            if (!"ACCEPTED".equals(state)) assertTrue(b.send("L6C_TRANSITION " + state).startsWith(state + "/"));
                            var before = snapshot(reader);
                            for (int i = 0; i < 3; i++) assertTrue(a.send("L6C_CONVERGE " + state).startsWith(state + "/"));
                            assertEquals(before, snapshot(reader));
                            assertTrue(a.send("L6C_TRANSITION " + state).startsWith("ERROR invalid order transition:"));
                            assertEquals(before, snapshot(reader));
                            proof.withArray("noOpStates").add(state);
                        }
                        assertTrue(a.send("L6C_CONVERGE NEW").startsWith("ERROR unsupported reconciliation status:"));
                    } else if ("CANCEL_RACE".equals(scenario)) {
                        // 合成 venue 接收外部撤单并生效；本地仍为 ACCEPTED，由真实对账推进两个合法阶段。
                        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
                            var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/api/v5/trade/cancel-order"))
                                    .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString("{\"ordId\":\"b2-venue-1\"}"))
                                    .build(), HttpResponse.BodyHandlers.ofString());
                            assertEquals(200, response.statusCode());
                        }
                        control(endpoint, "CANCEL_EFFECT");
                        a.send("L6C_ARM CANCELLED"); a.send("L6C_BEGIN"); awaitCut(a);
                        assertEquals("CANCEL_REQUESTED", value(reader, "SELECT status FROM orders"));
                        long version = Long.parseLong(value(reader, "SELECT version FROM orders"));
                        assertEquals("RECOVER 0", b.send("RECOVER"));
                        assertEquals("CANCELLED", value(reader, "SELECT status FROM orders"));
                        assertEquals(version + 1, Long.parseLong(value(reader, "SELECT version FROM orders")));
                        proof.set("winner", snapshot(reader));
                        a.send("L6C_RELEASE"); assertEquals("OK", a.send("L6C_AWAIT"));
                        assertEquals(proof.path("winner"), snapshot(reader));
                    } else if ("BATCH".equals(scenario)) {
                        assertTrue(b.send("PLACE_B3_NEW").endsWith("ACCEPTED"));
                        control(endpoint, "FILL");
                        a.send("L6C_ARM FILLED"); a.send("L6C_BEGIN"); awaitCut(a);
                        assertEquals("RECOVER 1", b.send("L6C_ONCE 1"));
                        assertEquals("1", value(reader, "SELECT count(*) FROM orders WHERE status='ACCEPTED'"));
                        proof.set("winnerOnly", snapshot(reader));
                        a.send("L6C_RELEASE"); assertEquals("OK", a.send("L6C_AWAIT"));
                        assertEquals("2", value(reader, "SELECT count(*) FROM orders WHERE status='FILLED'"));
                        assertEquals("2", value(reader, "SELECT count(*) FROM trades"));
                        assertEquals("8", value(reader, "SELECT count(*) FROM ledger_entries"));
                        proof.set("completedBatch", snapshot(reader));
                        assertEquals("RECOVER 0", a.send("RECOVER"));
                        assertEquals(proof.path("completedBatch"), snapshot(reader));
                    } else {
                        boolean stale = "STALE_CAS".equals(scenario);
                        control(endpoint, stale ? "FILL 4 0" : "FILL 10 0");
                        a.send(stale ? "L6C_CAS PARTIALLY_FILLED" : "L6C_ARM FILLED");
                        a.send("L6C_BEGIN"); awaitCut(a);
                        if (stale) {
                            control(endpoint, "FILL 6 0"); assertEquals("RECOVER 2", b.send("RECOVER"));
                        } else {
                            assertTrue(b.send("L6C_TRANSITION CANCEL_REQUESTED").startsWith("CANCEL_REQUESTED/"));
                            assertTrue(b.send("L6C_TRANSITION CANCELLED").startsWith("CANCELLED/"));
                        }
                        proof.set("winner", snapshot(reader));
                        a.send("L6C_RELEASE"); String outcome = a.send("L6C_AWAIT");
                        assertEquals(stale ? "OK" : "ERROR invalid order transition: CANCELLED -> FILLED", outcome);
                        proof.put("outcome", outcome);
                        assertEquals(proof.path("winner"), snapshot(reader));
                        a.send("RECOVER");
                        assertEquals("FILLED", value(reader, "SELECT status FROM orders"));
                        if (!stale) assertEquals("1", value(reader,
                                "SELECT count(*) FROM audit_logs WHERE action='ORDER_TERMINAL_EXECUTION_CORRECTED'"));
                        proof.set("recovered", snapshot(reader));
                        a.send("RECOVER"); assertEquals(proof.path("recovered"), snapshot(reader));
                    }
                    proof.put("result", "PASS");
                } finally {
                    proof.set("finalDb", snapshot(reader));
                    Files.writeString(dir.resolve("proof.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
                }
            }
        }
        System.out.println("L6C_BOUNDARY " + dir);
    }

    private ObjectNode snapshot(Connection reader) throws Exception {
        var result = new B2RealProcessProofTest().snapshot(reader);
        result.put("events", value(reader, "SELECT count(*) FROM event_store"));
        result.put("transitions", value(reader, "SELECT count(*) FROM audit_logs WHERE action='ORDER_STATUS_TRANSITION'"));
        return result;
    }
    private String value(Connection reader, String sql) throws Exception {
        try (var statement = reader.createStatement(); var result = statement.executeQuery(sql)) { result.next(); return result.getString(1); }
    }
    private void control(String endpoint, String command) throws Exception {
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            var result = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/control")).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(command)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, result.statusCode(), result.body());
        }
    }
    private void awaitCut(B0Processes.Child child) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            assertTrue(child.process.isAlive());
            if (Files.readString(child.log).contains("L6C_CUT ")) return;
            Thread.sleep(50);
        }
        throw new AssertionError("convergence cut not reached");
    }
}
