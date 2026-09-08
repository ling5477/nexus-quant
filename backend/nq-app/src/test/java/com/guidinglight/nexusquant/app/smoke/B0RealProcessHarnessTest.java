package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** 显式启用的 B0 基础设施验收；不在默认 Maven 测试中启动容器，也不执行 B1–B5 matrix。 */
@EnabledIfSystemProperty(named = "nq.b0", matches = "true")
class B0RealProcessHarnessTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Test void provesDefaultSafetyCanonicalSmokeRestartAndVenueFaults() throws Exception {
        Path directory = B0Processes.root().resolve("backend/nq-app/target/b0-harness/" + UUID.randomUUID());
        Files.createDirectories(directory);
        long controller = ProcessHandle.current().pid();
        System.out.println("B0_CONTROLLER pid=" + controller + " evidence=" + directory);
        try (var postgres = B0Processes.Pg.start();
             var venue = new B0Processes.Child(B0SyntheticVenueMain.class, directory, "venue", B0Processes.cleanEnvironment())) {
            String endpoint = "http://127.0.0.1:" + venue.ready();
            System.out.println("B0_VENUE pid=" + venue.process.pid() + " endpoint=" + endpoint + " state=VENUE_OWNED_IN_MEMORY");
            assertNotEquals(controller, venue.process.pid());
            assertEquals(venue.process.pid(), facts(endpoint).path("pid").asLong());
            defaultSafety(postgres, directory, venue, endpoint, controller);
            success(postgres, directory, venue, endpoint, controller, false);
            success(postgres, directory, venue, endpoint, controller, true);
            faultCapabilities(endpoint);
        }
        System.out.println("B0_ACCEPTANCE PASS defaultSafety smoke restart responseLoss cleanup");
    }

    private void defaultSafety(B0Processes.Pg postgres, Path directory, B0Processes.Child venue,
                               String endpoint, long controller) throws Exception {
        String databaseName;
        try (var fixture = B0Fixture.create(postgres)) {
            databaseName = fixture.name();
            Map<String, String> env = environment(fixture, endpoint);
            fixture.initialize(false, endpoint, env);
            assertThrows(IllegalStateException.class, () -> fixture.initialize(true, endpoint, env));
            try (var nq = new B0Processes.Child(B0NqProcessMain.class, directory, "default", env)) {
                nq.ready();
                assertPids(controller, venue, nq);
                try (var checker = fixture.checker()) {
                    assertIdentityAndPermissions(checker, fixture);
                    assertEquals("ENGAGED", value(checker, "SELECT status FROM kill_switch_states"));
                    assertTrue(nq.send("PLACE").endsWith("RISK_REJECTED"));
                    assertEquals("RISK_REJECTED", value(checker, "SELECT status FROM orders"));
                    assertEquals("REJECT", value(checker, "SELECT decision FROM risk_events WHERE trace_id='b0-trace'"));
                    assertEquals("KILL_SWITCH_TRIGGERED", value(checker, "SELECT reason FROM risk_events WHERE trace_id='b0-trace'"));
                    assertEquals("0", value(checker, "SELECT count(*) FROM trades"));
                    assertEquals("0", value(checker, "SELECT count(*) FROM ledger_entries"));
                    assertEquals(0, facts(endpoint).path("places").asInt());
                    assertEquals("ENGAGED", value(checker, "SELECT status FROM kill_switch_states"));
                    System.out.println("B0_DEFAULT db=" + databaseName + " kill=ENGAGED risk=REJECT venueCalls=0");
                }
            }
        }
        assertTrue(postgres.databaseAbsent(databaseName));
        System.out.println("B0_DB_CLEANUP db=" + databaseName + " remaining=0");
    }

    private void success(B0Processes.Pg postgres, Path directory, B0Processes.Child venue,
                         String endpoint, long controller, boolean restart) throws Exception {
        String databaseName;
        String label = restart ? "restart" : "smoke";
        int beforeCalls = facts(endpoint).path("places").asInt();
        try (var fixture = B0Fixture.create(postgres)) {
            databaseName = fixture.name();
            Map<String, String> env = environment(fixture, endpoint);
            fixture.initialize(true, endpoint, env);
            try (var checker = fixture.checker();
                 var first = new B0Processes.Child(B0NqProcessMain.class, directory, label + "-a", env)) {
                first.ready();
                assertPids(controller, venue, first);
                assertIdentityAndPermissions(checker, fixture);
                assertEquals("DISENGAGED", value(checker, "SELECT status FROM kill_switch_states"));
                String result = first.send("PLACE");
                assertTrue(result.endsWith("ACCEPTED"), result);
                assertEquals("ACCEPTED", value(checker, "SELECT status FROM orders"));
                assertEquals("ALLOW", value(checker, "SELECT decision FROM risk_events WHERE trace_id='b0-trace'"));
                assertEquals("0", value(checker, "SELECT count(*) FROM trades"));
                assertEquals(beforeCalls + 1, facts(endpoint).path("places").asInt());
                String order = value(checker, "SELECT order_id FROM orders");
                System.out.println("B0_DURABLE db=" + databaseName + " order=" + order + " status=ACCEPTED trades=0 pid=" + first.process.pid());
                if (restart) {
                    first.kill();
                    // 进程 A 已确实死亡；父进程通过独立只读 JDBC 仍可见提交的订单。
                    assertEquals(order, value(checker, "SELECT order_id FROM orders"));
                    try (var second = new B0Processes.Child(B0NqProcessMain.class, directory, label + "-b", env)) {
                        second.ready();
                        assertNotEquals(first.process.pid(), second.process.pid());
                        assertPids(controller, venue, second);
                        reconcileAndCheck(second, checker, endpoint, order);
                        System.out.println("B0_RESTART db=" + databaseName + " oldPid=" + first.process.pid()
                                + " newPid=" + second.process.pid() + " sameOrder=" + order + " recovered=true");
                    }
                } else {
                    reconcileAndCheck(first, checker, endpoint, order);
                }
                assertEquals(beforeCalls + 1, facts(endpoint).path("places").asInt(), "recovery must not PLACE again");
            }
        }
        assertTrue(postgres.databaseAbsent(databaseName));
        System.out.println("B0_DB_CLEANUP db=" + databaseName + " remaining=0");
    }

    private void reconcileAndCheck(B0Processes.Child nq, Connection checker, String endpoint, String order) throws Exception {
        assertEquals("RECOVER 1", nq.send("RECOVER"));
        assertEquals("FILLED", value(checker, "SELECT status FROM orders"));
        assertEquals("SIM", value(checker, "SELECT trade_env FROM orders"));
        assertEquals("1", value(checker, "SELECT count(*) FROM trades"));
        assertDecimal("123.45", value(checker, "SELECT price FROM trades"));
        assertDecimal("0.1", value(checker, "SELECT qty FROM trades"));
        assertDecimal("0", value(checker, "SELECT fee FROM trades"));
        assertEquals(order, value(checker, "SELECT order_id FROM trades"));
        assertEquals("2", value(checker, "SELECT count(*) FROM ledger_entries WHERE ref_id=(SELECT trade_id FROM trades)"));
        assertDecimal("0", value(checker, "SELECT sum(delta) FROM ledger_entries"));
        assertEquals("2", value(checker, "SELECT count(*) FROM ledger_events e JOIN ledger_entries l ON e.entry_id=l.entry_id"
                + " WHERE l.ref_id=(SELECT trade_id FROM trades)"));
        assertDecimal("0.1", value(checker, "SELECT qty FROM positions WHERE symbol='BTC-USDT'"));
        assertDecimal("0.1", value(checker, "SELECT balance FROM account_snapshots WHERE currency='BTC' ORDER BY ts DESC,snapshot_id DESC LIMIT 1"));
        assertTrue(Long.parseLong(value(checker, "SELECT count(*) FROM audit_logs WHERE trace_id='b0-trace'")) > 0);
        assertTrue(Long.parseLong(value(checker, "SELECT count(*) FROM event_store WHERE trace_id='b0-trace'")) > 0);
        String external = value(checker, "SELECT external_order_id FROM orders");
        assertTrue(facts(endpoint).path("data").findValuesAsText("ordId").contains(external));
        assertEquals("b0-fill-" + external, value(checker, "SELECT exchange_trade_id FROM trades"));
        assertEquals("0", value(checker, "SELECT count(*) FROM execution_intents"));
        assertEquals("0", value(checker, "SELECT count(*) FROM execution_receipts"));
        String immutable = businessSnapshot(checker);
        assertEquals("RECOVER 0", nq.send("RECOVER"));
        assertEquals(immutable, businessSnapshot(checker), "repeated recovery must preserve facts");
        assertEquals("DISENGAGED", value(checker, "SELECT status FROM kill_switch_states"));
        System.out.println("B0_FACTS order=" + order + " venueOrder=" + external + " status=FILLED trade=1 ledger=2"
                + " ledgerEvents=2 price=123.45 qty=0.1 fee=0 ledgerNet=0 position=0.1 account=0.1 intent=0 receipt=0 repeatedRecovery=stable");
    }

    private String businessSnapshot(Connection connection) throws Exception {
        StringBuilder result = new StringBuilder();
        for (String table : new String[]{"orders", "trades", "ledger_entries", "ledger_events", "positions", "account_snapshots"}) {
            result.append(value(connection, "SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,'[]') FROM " + table + " t"));
        }
        return result.toString();
    }

    private void assertIdentityAndPermissions(Connection connection, B0Fixture fixture) throws Exception {
        assertEquals(fixture.name(), value(connection, "SELECT current_database()"));
        assertEquals(B0Fixture.READER, value(connection, "SELECT current_user"));
        assertEquals("48", value(connection, "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
        assertEquals("f", value(connection, "SELECT has_table_privilege('nq_b0_app','kill_switch_states','UPDATE')"));
        assertEquals("f", value(connection, "SELECT has_table_privilege('nq_b0_reader','orders','UPDATE')"));
        assertEquals("f", value(connection, "SELECT has_table_privilege('nq_b0_reader','trades','INSERT')"));
        assertEquals("f", value(connection, "SELECT has_table_privilege('nq_b0_reader','ledger_entries','INSERT')"));
        System.out.println("B0_DB db=" + fixture.name() + " flyway=48 reader=nq_b0_reader appKillWrite=false pg="
                + value(connection, "SHOW server_version"));
    }

    private void faultCapabilities(String endpoint) throws Exception {
        int before = facts(endpoint).path("orders").asInt();
        for (String mode : new String[]{"DROP", "CLOSE", "DELAY"}) {
            request(endpoint + "/control", mode);
            String body = "{\"clOrdId\":\"fault-" + mode + "\",\"instId\":\"BTC-USDT\",\"px\":\"100\",\"sz\":\"0.1\"}";
            long started = System.nanoTime();
            if ("DELAY".equals(mode)) {
                assertEquals(200, request(endpoint + "/api/v5/trade/order", body).statusCode());
                assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() >= 500);
            } else {
                assertThrows(java.io.IOException.class, () -> request(endpoint + "/api/v5/trade/order", body));
            }
            assertEquals(++before, facts(endpoint).path("orders").asInt(), "venue stored before loss");
            System.out.println("B0_FAULT mode=" + mode + " venueFactStored=true response=" + (mode.equals("DELAY") ? "DELAYED" : "ABSENT"));
        }
        request(endpoint + "/control", "DELIVER");
    }

    private JsonNode facts(String endpoint) throws Exception {
        return mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(endpoint + "/facts"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    private HttpResponse<String> request(String endpoint, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(3))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Map<String, String> environment(B0Fixture fixture, String endpoint) {
        var env = B0Processes.cleanEnvironment();
        env.put("NQ_B0_DB", fixture.url()); env.put("NQ_B0_VENUE", endpoint); env.put("NQ_B0_PROFILE", "b0-test");
        return env;
    }
    private void assertPids(long controller, B0Processes.Child venue, B0Processes.Child nq) {
        assertNotEquals(controller, nq.process.pid()); assertNotEquals(venue.process.pid(), nq.process.pid());
    }
    private String value(Connection connection, String sql) throws Exception {
        try (var query = connection.createStatement(); var result = query.executeQuery(sql)) {
            assertTrue(result.next()); return result.getString(1);
        }
    }
    private void assertDecimal(String expected, String actual) { assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual))); }
}
