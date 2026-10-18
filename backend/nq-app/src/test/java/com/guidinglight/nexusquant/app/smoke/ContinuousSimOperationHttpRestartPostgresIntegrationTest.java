package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 新随机PG和两个独立Servlet JVM证明原编号/原结果、回滚、并发和关闭执行后的恢复读取。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class ContinuousSimOperationHttpRestartPostgresIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String OPERATIONS =
            "/api/paper-trading/strategy-sim/continuous-operations";

    @Test
    void upgradesSavedV20CursorWithoutChangingOldFactsAndRollsBackOnLockTimeout() throws Exception {
        String base = System.getProperty("nq.postgres.smoke.url", "");
        // 只接入本机隔离PG的维护库；本用例随后创建自有随机库，不依赖另一项Shadow测试留下的库名。
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", base);
        String user = System.getenv("TASK_CONTINUOUS_OPERATION_DB_USER");
        String password = System.getenv("TASK_CONTINUOUS_OPERATION_DB_PASSWORD");
        String database =
                "continuous_operation_http_" + UUID.randomUUID().toString().replace("-", "");
        String url = base.substring(0, base.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(System.getProperty("nq.continuous.operation.http.evidence"))
                        .toAbsolutePath()
                        .resolve(database);
        Files.createDirectories(evidence);
        var control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                base + "?connectTimeout=5&socketTimeout=30", user, password));
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
            String runId;
            JsonNode saved;
            long firstPid;
            try (var fixture = launch("upgrade-source", url, user, password, evidence)) {
                firstPid = fixture.ready().path("pid").asLong();
                runId = fixture.ready().path("paperRunId").asText();
                saved =
                        cursor(
                                http,
                                fixture.port,
                                login(http, fixture.port, "continuous-owner"),
                                runId);
                assertEquals(
                        "20",
                        jdbc.queryForObject(
                                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                                String.class));
            }
            List<String> oldTables =
                    jdbc.queryForList(
                            "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename",
                            String.class);
            assertEquals(94, oldTables.size());
            Map<String, String> oldFacts = oldTableFacts(jdbc, oldTables);
            Flyway migration =
                    Flyway.configure()
                            .dataSource(source)
                            .locations("classpath:db/migration")
                            .target("21")
                            .load();
            long started = System.nanoTime();
            try (var lock = source.getConnection();
                    var statement = lock.createStatement()) {
                lock.setAutoCommit(false);
                statement.setQueryTimeout(8);
                statement.execute("LOCK TABLE users IN ACCESS EXCLUSIVE MODE");
                assertThrows(FlywayException.class, () -> migration.migrate());
                lock.rollback();
            }
            long blockedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(
                    blockedMillis >= 4500 && blockedMillis < 15000,
                    "bounded migration lock timeout");
            assertEquals(oldFacts, oldTableFacts(jdbc, oldTables));
            assertTrue(
                    jdbc.queryForObject(
                            "SELECT to_regclass('public.continuous_sim_operations') IS NULL",
                            Boolean.class));
            assertEquals(1, migration.migrate().migrationsExecuted);
            migration.validate();
            assertEquals(0, migration.migrate().migrationsExecuted);
            assertEquals(oldFacts, oldTableFacts(jdbc, oldTables));
            var beforeRestart = facts(jdbc);
            try (var reopened = launch("disabled", url, user, password, evidence)) {
                assertNotEquals(firstPid, reopened.ready().path("pid").asLong());
                String owner = login(http, reopened.port, "continuous-owner");
                assertEquals(saved, cursor(http, reopened.port, owner, runId));
                assertEquals(beforeRestart, facts(jdbc));
            }
            Files.writeString(
                    evidence.resolve("upgrade-proof.json"),
                    mapper.writeValueAsString(
                            Map.of(
                                    "status",
                                    "PASS_SCOPED_V20_TO_V21_TWO_JVMS",
                                    "oldTablesPreserved",
                                    oldTables.size(),
                                    "oldMigrationsPreserved",
                                    20,
                                    "lockTimeoutMillis",
                                    blockedMillis,
                                    "paperRunId",
                                    runId,
                                    "repeatMigrations",
                                    0,
                                    "installed",
                                    false)));
            passed = true;
        } finally {
            if (passed) {
                assertTrue(database.matches("continuous_operation_http_[a-f0-9]{32}"));
                control.execute("DROP DATABASE " + database + " WITH (FORCE)");
            }
        }
    }

    /** 全部旧表逐行指纹保持；只排除声明新增的唯一V21历史记录，不排除任何旧业务列。 */
    private static Map<String, String> oldTableFacts(JdbcTemplate jdbc, List<String> tables) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : tables) {
            assertTrue(table.matches("[a-z][a-z0-9_]+"));
            String filter =
                    table.equals("flyway_schema_history")
                            ? " WHERE version IS DISTINCT FROM '21'"
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

    @Test
    void originalCommandsRemainAtomicImmutableAndRecoverableAcrossDisabledRestart()
            throws Exception {
        String base = System.getProperty("nq.postgres.smoke.url", "");
        URI endpoint = URI.create(base.replaceFirst("^jdbc:", ""));
        assertEquals("127.0.0.1", endpoint.getHost());
        assertEquals(15442, endpoint.getPort());
        assertTrue(endpoint.getRawQuery() == null && endpoint.getUserInfo() == null);
        String user = System.getenv("TASK_CONTINUOUS_OPERATION_DB_USER");
        String password = System.getenv("TASK_CONTINUOUS_OPERATION_DB_PASSWORD");
        assertTrue(user != null && !user.isBlank() && password != null && !password.isBlank());
        String database =
                "continuous_operation_http_" + UUID.randomUUID().toString().replace("-", "");
        String url = base.substring(0, base.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(System.getProperty("nq.continuous.operation.http.evidence"))
                        .toAbsolutePath()
                        .resolve(database);
        Files.createDirectories(evidence);
        var control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                base + "?connectTimeout=5&socketTimeout=30", user, password));
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
            JsonNode original;
            ObjectNode creation;
            String runId;
            long firstPid;
            try (var fixture = launch("create", url, user, password, evidence)) {
                firstPid = fixture.ready().path("pid").asLong();
                String owner = login(http, fixture.port, "continuous-owner"),
                        other = login(http, fixture.port, "continuous-other"),
                        admin = login(http, fixture.port, "continuous-admin"),
                        viewer = login(http, fixture.port, "continuous-viewer");
                assertTrue(
                        ok(send(
                                        http,
                                        fixture.port,
                                        "GET",
                                        "/api/paper-trading/strategy-sim/runs/continuous-capabilities",
                                        owner,
                                        null))
                                .path("commandsAvailable")
                                .asBoolean());
                creation = command("CREATE", fixture.ready().path("publish").asText(), null);
                assertEquals(
                        401,
                        send(http, fixture.port, "POST", OPERATIONS, null, creation).statusCode());
                assertEquals(
                        403,
                        send(http, fixture.port, "POST", OPERATIONS, viewer, creation)
                                .statusCode());
                assertEquals(
                        400,
                        send(
                                        http,
                                        fixture.port,
                                        "POST",
                                        "/api/paper-trading/strategy-sim/runs/continuous",
                                        owner,
                                        mapper.createObjectNode()
                                                .put(
                                                        "publishId",
                                                        creation.path("publishId").asText())
                                                .put("budget", "100"))
                                .statusCode());
                original = ok(send(http, fixture.port, "POST", OPERATIONS, owner, creation));
                runId = original.path("paperRunId").asText();
                assertEquals("COMPLETED", original.path("status").asText());
                assertEquals("RUNNING", original.path("result").path("status").asText());
                assertEquals(
                        "continuous-owner",
                        jdbc.queryForObject(
                                "SELECT created_by FROM paper_trading_runs WHERE paper_run_id=?",
                                String.class,
                                runId));
                assertEquals(
                        original,
                        ok(send(http, fixture.port, "GET", operationPath(creation), owner, null)));
                assertEquals(
                        original,
                        ok(
                                send(
                                        http,
                                        fixture.port,
                                        "POST",
                                        OPERATIONS,
                                        owner,
                                        creation.deepCopy().put("budget", "100.00000000"))));
                assertEquals(
                        409,
                        send(
                                        http,
                                        fixture.port,
                                        "POST",
                                        OPERATIONS,
                                        owner,
                                        creation.deepCopy().put("budget", "101"))
                                .statusCode());
                assertEquals(
                        409,
                        send(http, fixture.port, "POST", OPERATIONS, other, creation).statusCode());
                for (String token : List.of(other, admin, viewer))
                    assertEquals(
                            404,
                            send(http, fixture.port, "GET", operationPath(creation), token, null)
                                    .statusCode());
                var pause = command("PAUSE", null, runId);
                JsonNode paused = ok(send(http, fixture.port, "POST", OPERATIONS, owner, pause));
                assertEquals("STOPPED", paused.path("result").path("status").asText());
                assertEquals(
                        "RUNNING",
                        jdbc.queryForObject(
                                "SELECT status FROM paper_trading_runs WHERE paper_run_id=?",
                                String.class,
                                runId));
                assertEquals(
                        original,
                        ok(send(http, fixture.port, "GET", operationPath(creation), owner, null)));
                var resume = command("RESUME", null, runId);
                assertEquals(
                        "RUNNING",
                        ok(send(http, fixture.port, "POST", OPERATIONS, owner, resume))
                                .path("result")
                                .path("status")
                                .asText());
                assertEquals(
                        paused, ok(send(http, fixture.port, "POST", OPERATIONS, owner, pause)));
                assertEquals(
                        "RUNNING",
                        cursor(http, fixture.port, owner, runId).path("status").asText());

                // 同UUID并发允许忙冲突，终态重放必须回到同一运行；不会按请求数创建独立资金账户。
                var parallel = command("CREATE", creation.path("publishId").asText(), null);
                long beforeRuns = count(jdbc, "paper_trading_runs");
                List<CompletableFuture<HttpResponse<String>>> requests = new ArrayList<>();
                for (int i = 0; i < 8; i++)
                    requests.add(sendAsync(http, fixture.port, OPERATIONS, owner, parallel));
                for (var future : requests) {
                    var response = future.get(12, TimeUnit.SECONDS);
                    assertTrue(List.of(200, 409).contains(response.statusCode()));
                }
                JsonNode same = ok(send(http, fixture.port, "POST", OPERATIONS, owner, parallel));
                assertEquals(beforeRuns + 1, count(jdbc, "paper_trading_runs"));
                assertEquals(
                        1L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM continuous_sim_operations WHERE operation_id=?::uuid",
                                Long.class,
                                parallel.path("operationId").asText()));
                for (int i = 0; i < 3; i++)
                    assertEquals(
                            same,
                            ok(send(http, fixture.port, "POST", OPERATIONS, owner, parallel)));
                proveRollback(http, fixture, jdbc, owner, creation.path("publishId").asText());
                proveLockUntilCommit(http, fixture, jdbc, source, owner, runId);

                String originalId = creation.path("operationId").asText();
                assertThrows(
                        DataIntegrityViolationException.class,
                        () ->
                                jdbc.update(
                                        "UPDATE continuous_sim_operations SET trace_id='changed' WHERE operation_id=?::uuid",
                                        originalId));
                assertThrows(
                        DataIntegrityViolationException.class,
                        () ->
                                jdbc.update(
                                        "DELETE FROM continuous_sim_operations WHERE operation_id=?::uuid",
                                        originalId));
                var before = facts(jdbc);
                for (int i = 0; i < 3; i++) {
                    assertEquals(
                            original,
                            ok(
                                    send(
                                            http,
                                            fixture.port,
                                            "GET",
                                            operationPath(creation),
                                            owner,
                                            null)));
                    assertEquals(
                            "STOPPED",
                            cursor(http, fixture.port, owner, runId).path("status").asText());
                }
                assertEquals(before, facts(jdbc));
                assertEquals(
                        "21",
                        jdbc.queryForObject(
                                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                                String.class));
            }
            var persisted = facts(jdbc);
            try (var reopened = launch("disabled", url, user, password, evidence)) {
                assertNotEquals(firstPid, reopened.ready().path("pid").asLong());
                String owner = login(http, reopened.port, "continuous-owner");
                assertFalse(
                        ok(send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        "/api/paper-trading/strategy-sim/runs/continuous-capabilities",
                                        owner,
                                        null))
                                .path("commandsAvailable")
                                .asBoolean(true));
                assertEquals(
                        original,
                        ok(send(http, reopened.port, "GET", operationPath(creation), owner, null)));
                assertEquals(
                        original,
                        ok(send(http, reopened.port, "POST", OPERATIONS, owner, creation)));
                assertEquals(
                        "STOPPED",
                        cursor(http, reopened.port, owner, runId).path("status").asText());
                var failed =
                        send(
                                http,
                                reopened.port,
                                "POST",
                                OPERATIONS,
                                owner,
                                command("RESUME", null, runId));
                assertEquals(409, failed.statusCode());
                assertEquals(
                        "CONTINUOUS_SIM_DISABLED",
                        mapper.readTree(failed.body()).path("message").asText());
                assertEquals(persisted, facts(jdbc));
                assertEquals(0L, count(jdbc, "orders"));
                assertEquals(0L, count(jdbc, "trades"));
                assertEquals(
                        "ENGAGED",
                        jdbc.queryForObject(
                                "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                                String.class));
            }
            Files.writeString(
                    evidence.resolve("proof.json"),
                    mapper.writeValueAsString(
                            Map.of(
                                    "status",
                                    "PASS_SCOPED_CONTINUOUS_COMMANDS_TWO_JVMS",
                                    "paperRunId",
                                    runId,
                                    "firstPid",
                                    firstPid,
                                    "parallelRequests",
                                    8,
                                    "orders",
                                    0,
                                    "trades",
                                    0,
                                    "input",
                                    "SYNTHETIC_FROZEN_PRECONDITION_NOT_UPSTREAM_PUBLISH_PROOF",
                                    "installed",
                                    false)));
            passed = true;
        } finally {
            if (passed) {
                assertTrue(database.matches("continuous_operation_http_[a-f0-9]{32}"));
                control.execute("DROP DATABASE " + database + " WITH (FORCE)");
            }
        }
    }

    /** 在原业务修改之后、回执提交之前故障，必须回滚账户/资金/父运行/游标和回执全部事实。 */
    private void proveRollback(
            HttpClient http, Fixture fixture, JdbcTemplate jdbc, String owner, String publish)
            throws Exception {
        var request = command("CREATE", publish, null);
        var before = facts(jdbc);
        jdbc.execute(
                "CREATE FUNCTION fail_continuous_receipt() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.operation_id='"
                        + request.path("operationId").asText()
                        + "'::uuid THEN RAISE EXCEPTION 'synthetic receipt failure' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");
        jdbc.execute(
                "CREATE TRIGGER fail_continuous_receipt BEFORE INSERT ON continuous_sim_operations FOR EACH ROW EXECUTE FUNCTION fail_continuous_receipt()");
        try {
            assertTrue(
                    send(http, fixture.port, "POST", OPERATIONS, owner, request).statusCode()
                            >= 400);
            assertEquals(
                    404,
                    send(http, fixture.port, "GET", operationPath(request), owner, null)
                            .statusCode());
            assertEquals(before, facts(jdbc));
        } finally {
            jdbc.execute("DROP TRIGGER fail_continuous_receipt ON continuous_sim_operations");
            jdbc.execute("DROP FUNCTION fail_continuous_receipt()");
        }
        // 故障消除后只显式继续原编号；失败没有被换编号的请求掩盖。
        assertEquals(
                "COMPLETED",
                ok(send(http, fixture.port, "POST", OPERATIONS, owner, request))
                        .path("status")
                        .asText());
    }

    private void proveLockUntilCommit(
            HttpClient http,
            Fixture fixture,
            JdbcTemplate jdbc,
            DriverManagerDataSource source,
            String owner,
            String runId)
            throws Exception {
        var request = command("PAUSE", null, runId);
        long barrier = 739241091273L;
        jdbc.execute(
                "CREATE FUNCTION hold_continuous_receipt() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.operation_id='"
                        + request.path("operationId").asText()
                        + "'::uuid THEN PERFORM pg_advisory_xact_lock("
                        + barrier
                        + "); END IF; RETURN NEW; END; $$");
        jdbc.execute(
                "CREATE TRIGGER hold_continuous_receipt BEFORE INSERT ON continuous_sim_operations FOR EACH ROW EXECUTE FUNCTION hold_continuous_receipt()");
        try (Connection blocker = source.getConnection();
                var statement = blocker.createStatement()) {
            statement.setQueryTimeout(5);
            statement.execute("SELECT pg_advisory_lock(" + barrier + ")");
            var pending = sendAsync(http, fixture.port, OPERATIONS, owner, request);
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
                boolean waiting;
                do {
                    waiting =
                            Boolean.TRUE.equals(
                                    jdbc.queryForObject(
                                            "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory' AND query LIKE 'INSERT INTO continuous_sim_operations%')",
                                            Boolean.class));
                    if (!waiting) Thread.sleep(30);
                } while (!waiting && System.nanoTime() < deadline);
                assertTrue(waiting, "original command must be waiting before receipt commit");
                try (var rows =
                        statement.executeQuery(
                                "SELECT pg_try_advisory_lock(hashtextextended('continuous-sim-run:"
                                        + runId
                                        + "',9252))")) {
                    rows.next();
                    assertFalse(
                            rows.getBoolean(1),
                            "poller session lock must remain excluded until command commits");
                }
                assertEquals(
                        "RUNNING",
                        jdbc.queryForObject(
                                "SELECT status FROM continuous_sim_runs WHERE paper_run_id=?",
                                String.class,
                                runId));
                assertEquals(
                        0L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM continuous_sim_operations WHERE operation_id=?::uuid",
                                Long.class,
                                request.path("operationId").asText()));
            } finally {
                statement.execute("SELECT pg_advisory_unlock(" + barrier + ")");
            }
            assertEquals(
                    "STOPPED",
                    ok(pending.get(12, TimeUnit.SECONDS)).path("result").path("status").asText());
        } finally {
            jdbc.execute("DROP TRIGGER hold_continuous_receipt ON continuous_sim_operations");
            jdbc.execute("DROP FUNCTION hold_continuous_receipt()");
        }
    }

    private ObjectNode command(String type, String publish, String run) {
        ObjectNode result =
                mapper.createObjectNode()
                        .put("operationId", UUID.randomUUID().toString())
                        .put("type", type);
        if (publish != null) result.put("publishId", publish).put("budget", "100");
        if (run != null) result.put("paperRunId", run);
        return result;
    }

    private static String operationPath(JsonNode request) {
        return OPERATIONS + "/" + request.path("operationId").asText();
    }

    private JsonNode cursor(HttpClient http, int port, String owner, String runId)
            throws Exception {
        return ok(
                send(
                        http,
                        port,
                        "GET",
                        "/api/paper-trading/strategy-sim/runs/" + runId + "/continuous",
                        owner,
                        null));
    }

    private static long count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static Map<String, String> facts(JdbcTemplate jdbc) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table :
                List.of(
                        "accounts",
                        "account_snapshots",
                        "paper_trading_runs",
                        "continuous_sim_runs",
                        "continuous_sim_bars",
                        "continuous_sim_operations",
                        "orders",
                        "trades",
                        "ledger_entries")) {
            long count = count(jdbc, table);
            assertTrue(count <= 10000);
            result.put(
                    table,
                    count
                            + ":"
                            + jdbc.queryForObject(
                                    "SELECT md5(COALESCE(string_agg(md5(to_jsonb(t)::text), '' ORDER BY md5(to_jsonb(t)::text)),'')) FROM "
                                            + table
                                            + " t",
                                    String.class));
        }
        return result;
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

    private CompletableFuture<HttpResponse<String>> sendAsync(
            HttpClient http, int port, String path, String token, JsonNode body) {
        return http.sendAsync(
                request(port, "POST", path, token, body),
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
