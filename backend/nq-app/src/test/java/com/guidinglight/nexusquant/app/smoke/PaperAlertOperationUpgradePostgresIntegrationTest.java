package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** 在V21正式服务建立的运行和旧告警上证明升级；不使用原验收库、不回填虚构的历史操作。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class PaperAlertOperationUpgradePostgresIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void oldFactsAndContinuousReceiptSurviveV22LockRollbackUpgradeAndRestart() throws Exception {
        String base = System.getProperty("nq.postgres.smoke.url", "");
        assertTrue(
                base.matches(
                        "jdbc:postgresql://127\\.0\\.0\\.1:15442/shadow_creation_http_[a-f0-9]{32}"));
        String user = System.getenv("TASK_CONTINUOUS_OPERATION_DB_USER");
        String password = System.getenv("TASK_CONTINUOUS_OPERATION_DB_PASSWORD");
        assertNotNull(user);
        assertNotNull(password);
        String database =
                "continuous_operation_http_" + UUID.randomUUID().toString().replace("-", "");
        String url = base.substring(0, base.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(System.getProperty("nq.paper.alert.upgrade.evidence"))
                        .toAbsolutePath()
                        .resolve(database);
        Files.createDirectories(evidence);
        var control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                base + "?connectTimeout=5&socketTimeout=30", user, password));
        control.setQueryTimeout(10);
        control.execute("CREATE DATABASE " + database);
        boolean passed = false;
        try (var http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .proxy(new DirectProxy())
                        .build()) {
            var source =
                    new DriverManagerDataSource(
                            url + "?connectTimeout=5&socketTimeout=30", user, password);
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(10);
            JsonNode ready;
            JsonNode continuous;
            Map<String, JsonNode> alerts = new LinkedHashMap<>();
            try (var first = launch("alert-upgrade-source", url, user, password, evidence)) {
                ready = first.ready();
                String token = login(http, first.port, "continuous-owner");
                continuous =
                        ok(
                                send(
                                        http,
                                        first.port,
                                        "GET",
                                        "/api/paper-trading/strategy-sim/continuous-operations/"
                                                + ready.path("continuousOperationId").asText(),
                                        token,
                                        null));
                for (JsonNode id : ready.path("alertIds"))
                    alerts.put(
                            id.asText(),
                            ok(
                                    send(
                                            http,
                                            first.port,
                                            "GET",
                                            "/api/paper-trading/runs/"
                                                    + ready.path("paperRunId").asText()
                                                    + "/alerts/"
                                                    + id.asText(),
                                            token,
                                            null)));
                assertEquals(
                        Set.of("OPEN", "ACKED", "RESOLVED"),
                        alerts.values().stream()
                                .map(row -> row.path("status").asText())
                                .collect(Collectors.toSet()));
            }
            assertEquals("21", version(jdbc));
            assertEquals(
                    1L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM continuous_sim_operations", Long.class));
            assertEquals(
                    2L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
            List<String> oldTables =
                    jdbc.queryForList(
                            "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename",
                            String.class);
            assertEquals(95, oldTables.size());
            Map<String, String> before = oldFacts(jdbc, oldTables);
            var columns = oldColumns(jdbc);
            var migration =
                    Flyway.configure()
                            .dataSource(source)
                            .locations("classpath:db/migration")
                            .target("22")
                            .cleanDisabled(true)
                            .load();
            long started = System.nanoTime();
            try (Connection lock = source.getConnection();
                    var statement = lock.createStatement()) {
                lock.setAutoCommit(false);
                statement.setQueryTimeout(8);
                statement.execute("LOCK TABLE users IN ACCESS EXCLUSIVE MODE");
                assertThrows(FlywayException.class, migration::migrate);
                lock.rollback();
            }
            long blockedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(blockedMillis >= 4500 && blockedMillis < 15000, "bounded V22 lock timeout");
            assertEquals("21", version(jdbc));
            assertEquals(before, oldFacts(jdbc, oldTables));
            assertEquals(columns, oldColumns(jdbc));
            assertTrue(
                    jdbc.queryForObject(
                            "SELECT to_regclass('public.paper_alert_operations') IS NULL",
                            Boolean.class));
            assertEquals(
                    0L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM pg_constraint WHERE conname='uq_paper_run_alert_parent'",
                            Long.class));
            assertEquals(1, migration.migrate().migrationsExecuted);
            migration.validate();
            assertEquals(0, migration.migrate().migrationsExecuted);
            assertEquals("22", version(jdbc));
            assertEquals(before, oldFacts(jdbc, oldTables));
            assertEquals(columns, oldColumns(jdbc));
            assertEquals(
                    0L,
                    jdbc.queryForObject("SELECT count(*) FROM paper_alert_operations", Long.class));
            assertEquals(
                    Set.of(
                            "paper_alert_operations_pkey",
                            "paper_alert_operations_owner_user_id_fkey",
                            "paper_alert_operations_operation_type_check",
                            "fk_paper_alert_operation_parent",
                            "chk_paper_alert_operation_identity",
                            "chk_paper_alert_operation_result",
                            "chk_paper_alert_operation_time"),
                    Set.copyOf(
                            jdbc.queryForList(
                                    "SELECT conname FROM pg_constraint WHERE conrelid='paper_alert_operations'::regclass",
                                    String.class)));
            // 正负例均在本地事务内回滚，不把约束探针当作正式完成回执或审计事实。
            JsonNode open =
                    alerts.values().stream()
                            .filter(row -> row.path("status").asText().equals("OPEN"))
                            .findFirst()
                            .orElseThrow();
            constraints(source, ready.path("owner").asLong(), open);
            assertEquals(
                    0L,
                    jdbc.queryForObject("SELECT count(*) FROM paper_alert_operations", Long.class));
            assertEquals(before, oldFacts(jdbc, oldTables));
            long reopenedPid;
            try (var second = launch("alert-read", url, user, password, evidence)) {
                reopenedPid = second.ready().path("pid").asLong();
                assertNotEquals(ready.path("pid").asLong(), reopenedPid);
                String token = login(http, second.port, "continuous-owner");
                assertEquals(
                        continuous,
                        ok(
                                send(
                                        http,
                                        second.port,
                                        "GET",
                                        "/api/paper-trading/strategy-sim/continuous-operations/"
                                                + ready.path("continuousOperationId").asText(),
                                        token,
                                        null)));
                for (var entry : alerts.entrySet())
                    assertEquals(
                            entry.getValue(),
                            ok(
                                    send(
                                            http,
                                            second.port,
                                            "GET",
                                            "/api/paper-trading/runs/"
                                                    + ready.path("paperRunId").asText()
                                                    + "/alerts/"
                                                    + entry.getKey(),
                                            token,
                                            null)));
                assertEquals(before, oldFacts(jdbc, oldTables));
            }
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
            assertEquals(
                    "ENGAGED",
                    jdbc.queryForObject(
                            "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                            String.class));
            Files.writeString(
                    evidence.resolve("upgrade-proof.json"),
                    mapper.writeValueAsString(
                            Map.of(
                                    "status",
                                    "PASS_SCOPED_V21_TO_V22",
                                    "oldTablesPreserved",
                                    95,
                                    "allOldColumnsPreserved",
                                    true,
                                    "oldContinuousReceiptPreserved",
                                    true,
                                    "oldAlertsPreserved",
                                    3,
                                    "lockTimeoutMillis",
                                    blockedMillis,
                                    "firstPid",
                                    ready.path("pid").asLong(),
                                    "secondPid",
                                    reopenedPid,
                                    "repeatedMigrations",
                                    0,
                                    "installed",
                                    false)));
            passed = true;
        } finally {
            if (passed) control.execute("DROP DATABASE " + database + " WITH (FORCE)");
        }
    }

    private static String version(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    /** 包含V21整表与所有旧列、重复行；仅排除本次明确新增的V22迁移历史行。 */
    private static Map<String, String> oldFacts(JdbcTemplate jdbc, List<String> tables) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : tables) {
            assertTrue(table.matches("[a-z][a-z0-9_]+"));
            String filter =
                    table.equals("flyway_schema_history")
                            ? " WHERE version IS DISTINCT FROM '22'"
                            : "";
            assertTrue(
                    jdbc.queryForObject("SELECT count(*) FROM " + table + filter, Long.class)
                            <= 10000);
            result.put(
                    table,
                    jdbc.queryForObject(
                            "SELECT count(*)::text||':'||md5(COALESCE(string_agg(md5(to_jsonb(t)::text), '' ORDER BY md5(to_jsonb(t)::text)),'')) FROM "
                                    + table
                                    + " t"
                                    + filter,
                            String.class));
        }
        return result;
    }

    private static List<Map<String, Object>> oldColumns(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name,column_name,ordinal_position,data_type,udt_name,character_maximum_length,numeric_precision,numeric_scale,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' AND table_name<>'paper_alert_operations' ORDER BY table_name,ordinal_position");
    }

    private void constraints(DriverManagerDataSource source, long owner, JsonNode open)
            throws Exception {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID id = UUID.randomUUID();
                String run = open.path("paperRunId").asText(),
                        alert = open.path("alertId").asText();
                insert(
                        connection,
                        id,
                        owner,
                        run,
                        alert,
                        "CREATE",
                        "a".repeat(64),
                        open.toString(),
                        "2026-10-15T00:00:00.123456Z");
                rejected(
                        connection,
                        () ->
                                insert(
                                        connection,
                                        id,
                                        owner,
                                        run,
                                        alert,
                                        "CREATE",
                                        "a".repeat(64),
                                        open.toString(),
                                        "2026-10-15T00:00:00Z"),
                        "23505");
                rejected(
                        connection,
                        () ->
                                insert(
                                        connection,
                                        UUID.randomUUID(),
                                        -1,
                                        run,
                                        alert,
                                        "CREATE",
                                        "a".repeat(64),
                                        open.toString(),
                                        "2026-10-15T00:00:00Z"),
                        "23503");
                rejected(
                        connection,
                        () ->
                                insert(
                                        connection,
                                        UUID.randomUUID(),
                                        owner,
                                        run,
                                        "alt-nonexistent",
                                        "CREATE",
                                        "a".repeat(64),
                                        ((ObjectNode) open.deepCopy())
                                                .put("alertId", "alt-nonexistent")
                                                .toString(),
                                        "2026-10-15T00:00:00Z"),
                        "23503");
                rejected(
                        connection,
                        () ->
                                insert(
                                        connection,
                                        UUID.randomUUID(),
                                        owner,
                                        run,
                                        alert,
                                        "ACK",
                                        "a".repeat(64),
                                        open.toString(),
                                        "2026-10-15T00:00:00Z"),
                        "23514");
                rejected(
                        connection,
                        () ->
                                insert(
                                        connection,
                                        UUID.randomUUID(),
                                        owner,
                                        run,
                                        alert,
                                        "CREATE",
                                        "z".repeat(64),
                                        open.toString(),
                                        "2026-10-15T00:00:00Z"),
                        "23514");
                ObjectNode missing = open.deepCopy();
                missing.remove("status");
                rejected(
                        connection,
                        () ->
                                insert(
                                        connection,
                                        UUID.randomUUID(),
                                        owner,
                                        run,
                                        alert,
                                        "CREATE",
                                        "a".repeat(64),
                                        missing.toString(),
                                        "2026-10-15T00:00:00Z"),
                        "23514");
                rejected(
                        connection,
                        () ->
                                insert(
                                        connection,
                                        UUID.randomUUID(),
                                        owner,
                                        run,
                                        alert,
                                        "CREATE",
                                        "a".repeat(64),
                                        ((ObjectNode) open.deepCopy())
                                                .put("message", "x".repeat(65536))
                                                .toString(),
                                        "2026-10-15T00:00:00Z"),
                        "23514");
                rejected(
                        connection,
                        () ->
                                insert(
                                        connection,
                                        UUID.randomUUID(),
                                        owner,
                                        run,
                                        alert,
                                        "CREATE",
                                        "a".repeat(64),
                                        open.toString(),
                                        "infinity"),
                        "23514");
                for (String mutation :
                        List.of(
                                "UPDATE paper_alert_operations SET actor='changed' WHERE operation_id=?",
                                "DELETE FROM paper_alert_operations WHERE operation_id=?"))
                    rejected(
                            connection,
                            () -> {
                                try (var statement = connection.prepareStatement(mutation)) {
                                    statement.setQueryTimeout(5);
                                    statement.setObject(1, id);
                                    statement.executeUpdate();
                                }
                            },
                            "23514");
            } finally {
                connection.rollback();
            }
        }
    }

    private static void insert(
            Connection connection,
            UUID id,
            long owner,
            String run,
            String alert,
            String type,
            String sha,
            String result,
            String time)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "INSERT INTO paper_alert_operations(operation_id,owner_user_id,actor,paper_run_id,operation_type,alert_id,input_sha256,trace_id,result_json,completed_at) VALUES(?,?,'continuous-owner',?,?,?,?,?,CAST(? AS jsonb),CAST(? AS timestamptz))")) {
            statement.setQueryTimeout(5);
            statement.setObject(1, id);
            statement.setLong(2, owner);
            statement.setString(3, run);
            statement.setString(4, type);
            statement.setString(5, alert);
            statement.setString(6, sha);
            statement.setString(7, "constraint-probe");
            statement.setString(8, result);
            statement.setString(9, time);
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlAttempt {
        void run() throws SQLException;
    }

    private static void rejected(Connection connection, SqlAttempt action, String state)
            throws SQLException {
        var savepoint = connection.setSavepoint();
        try {
            assertEquals(state, assertThrows(SQLException.class, action::run).getSQLState());
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private String login(HttpClient http, int port, String username) throws Exception {
        var response =
                ok(
                        send(
                                http,
                                port,
                                "POST",
                                "/api/auth/login",
                                null,
                                mapper.createObjectNode()
                                        .put("username", username)
                                        .put(
                                                "password",
                                                ContinuousSimOperationHttpFixtureMain.PASSWORD)));
        assertFalse(response.path("accessToken").asText().isBlank());
        return response.path("accessToken").asText();
    }

    private HttpRequest request(int port, String method, String path, String token, JsonNode body) {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        return request.method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    private HttpResponse<String> send(
            HttpClient http, int port, String method, String path, String token, JsonNode body)
            throws Exception {
        return http.send(
                request(port, method, path, token, body),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        // 登录body含JWT；断言失败只输出HTTP状态，禁止将认证材料写入测试artifact。
        String detail = "";
        if (response.statusCode() != 200 && !response.uri().getPath().endsWith("/login")) {
            JsonNode error = mapper.readTree(response.body());
            detail = error.path("code").asText() + ": " + error.path("message").asText();
        }
        assertEquals(200, response.statusCode(), detail);
        return mapper.readTree(response.body());
    }

    private Fixture launch(String mode, String url, String user, String password, Path evidence)
            throws Exception {
        String cp =
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path"));
        Path args = evidence.resolve(mode + ".args");
        Files.writeString(
                args,
                "-Duser.timezone=UTC\n-cp\n\""
                        + cp.replace("\\", "\\\\").replace("\"", "\\\"")
                        + "\"\n"
                        + ContinuousSimOperationHttpFixtureMain.class.getName()
                        + "\n"
                        + mode
                        + "\n\""
                        + evidence.resolve(mode + ".ready.json").toString().replace("\\", "\\\\")
                        + "\"\n\""
                        + evidence.resolve(mode + ".stop").toString().replace("\\", "\\\\")
                        + "\"\n");
        var builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "@" + args);
        builder.environment().put("TASK_CONTINUOUS_OPERATION_DB_URL", url);
        builder.environment().put("TASK_CONTINUOUS_OPERATION_DB_USER", user);
        builder.environment().put("TASK_CONTINUOUS_OPERATION_DB_PASSWORD", password);
        builder.redirectErrorStream(true).redirectOutput(evidence.resolve(mode + ".log").toFile());
        var fixture = new Fixture(builder.start(), mode, evidence, user, password);
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(55).toNanos();
            while (!Files.exists(evidence.resolve(mode + ".ready.json"))
                    && fixture.process.isAlive()
                    && System.nanoTime() < deadline) Thread.sleep(100);
            assertTrue(
                    Files.exists(evidence.resolve(mode + ".ready.json")),
                    "bounded owned servlet readiness: " + mode);
            fixture.port = fixture.ready().path("port").asInt();
            assertTrue(fixture.port > 0);
            return fixture;
        } catch (Exception | AssertionError failure) {
            fixture.close();
            throw failure;
        }
    }

    private final class Fixture implements AutoCloseable {
        private final Process process;
        private final String mode, user, password;
        private final Path evidence;
        private int port;

        private Fixture(Process process, String mode, Path evidence, String user, String password) {
            this.process = process;
            this.mode = mode;
            this.evidence = evidence;
            this.user = user;
            this.password = password;
        }

        private JsonNode ready() throws Exception {
            return mapper.readTree(Files.readString(evidence.resolve(mode + ".ready.json")));
        }

        @Override
        public void close() throws Exception {
            Files.writeString(evidence.resolve(mode + ".stop"), "stop owned fixture");
            boolean stopped = process.waitFor(15, TimeUnit.SECONDS);
            if (!stopped) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Path log = evidence.resolve(mode + ".log");
            if (Files.exists(log))
                Files.writeString(
                        log,
                        Files.readString(log)
                                .replace(user, "[REDACTED]")
                                .replace(password, "[REDACTED]")
                                .replaceAll(
                                        "(?m)(Using generated security password:\\s*)\\S+",
                                        "$1[REDACTED]"));
            assertTrue(stopped, "owned servlet required force termination");
            assertEquals(0, process.exitValue(), "owned servlet exit");
        }
    }

    private static final class DirectProxy extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socket, IOException failure) {}
    }
}
