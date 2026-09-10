package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 旧行仅在 JVM 启动前由隔离 SQL fixture 构造；运行阶段 controller 只读，禁止补 authority 或改历史 identity。 */
@EnabledIfSystemProperty(named = "nq.b5", matches = "true")
class B5LegacyVenuePostgresTest {
    @Test void legacyLowercaseMissingAuthorityRemainsQueryOnly() throws Exception {
        var mapper = new ObjectMapper();
        Path dir = B0Processes.root().resolve("backend/nq-app/target/b5-legacy-venue/" + UUID.randomUUID());
        Files.createDirectories(dir);
        System.out.println("B5_LEGACY_ROOT " + dir);
        var proof = mapper.createObjectNode().put("controllerPid", ProcessHandle.current().pid());
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg);
             var venue = new B0Processes.Child(B2SyntheticVenueMain.class, dir, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            var env = B0Processes.cleanEnvironment();
            env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
            fixture.initialize(true, endpoint, env);
            String client = "b0" + fixture.name().substring(fixture.name().length() - 30);
            try (var setup = DriverManager.getConnection(fixture.url(), "postgres", "");
                 var insert = setup.prepareStatement("INSERT INTO orders(order_id,account_id,venue,symbol,client_order_id,side,type,price,qty,status,trace_id,exchange_code,trade_env,version) "
                         + "SELECT 'legacy-order',account_id,'okx','BTC-USDT',?,'BUY','LIMIT',100,10,'SENT','legacy-venue','okx','SIM',2 FROM accounts WHERE account_code='b0-account'")) {
                insert.setString(1, client); assertEquals(1, insert.executeUpdate());
            }
            try (var reader = fixture.checker(); var a = new B0Processes.Child(B0NqProcessMain.class, dir, "nq-a", env).awaitReady();
                 var b = new B0Processes.Child(B0NqProcessMain.class, dir, "nq-b", env)) {
                b.ready();
                proof.put("database", fixture.name()).put("nqPid", a.process.pid()).put("restartPid", b.process.pid()).put("venuePid", venue.process.pid());
                a.send("SET_B5_VENUE b2t4");
                assertTrue(a.send("PLACE_B2").endsWith("SENT"));
                assertTrue(b.send("PLACE_B2").endsWith("SENT"));
                b.send("B5_RECOVERY"); b.send("RECOVER");
                assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                assertEquals("okx", value(reader, "SELECT venue FROM orders"));
                assertEquals("SENT", value(reader, "SELECT status FROM orders"));
                assertEquals("2", value(reader, "SELECT version FROM orders"));
                assertEquals("0", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                assertEquals("1", value(reader, "SELECT count(*) FROM audit_logs WHERE action='MANUAL_RESOLUTION_REQUIRED'"));
                assertEquals("0", value(reader, "SELECT count(*) FROM trades"));
                assertEquals("0", value(reader, "SELECT count(*) FROM ledger_entries"));
                var facts = mapper.readTree(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
                assertEquals(0, facts.path("placeRequests").asInt());
                assertEquals(0, facts.path("cancels").asInt());
                proof.set("venue", facts);
                proof.put("storedVenue", "okx").put("orderStatus", "SENT").put("orderVersion", 2)
                        .put("authorityCount", 0).put("manualResolutionRequired", true).put("result", "PASS");
            }
        }
        Path raw = dir.resolve("raw-proof.json");
        Files.writeString(raw, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
        SyntheticEvidenceExport.write(raw, "B5-LEGACY", 1);
    }

    private static String value(Connection connection, String sql) throws SQLException {
        try (var s = connection.createStatement(); var r = s.executeQuery(sql)) {
            assertTrue(r.next()); return r.getString(1);
        }
    }
}
