package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 复用原有Servlet夹具，只证明旧Paper停机原事件；合成研究前置不冒充真实收益或发布资格。 */
@EnabledIfSystemProperty(named = "nq.paper.emergency.pg.required", matches = "true")
class PaperEmergencyStopHttpPostgresIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void originalEventIsAtomicOwnedIdempotentAndSurvivesProcessRestart() throws Exception {
        String maintenance = System.getenv("TASK_EMERGENCY_MAINTENANCE_URL");
        String user = System.getenv("TASK_EMERGENCY_DB_USER");
        String password = System.getenv("TASK_EMERGENCY_DB_PASSWORD");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
        assertNotNull(user);
        assertNotNull(password);
        String name = "continuous_operation_http_" + UUID.randomUUID().toString().replace("-", "");
        String url = maintenance.substring(0, maintenance.lastIndexOf('/') + 1) + name;
        Path evidence = Path.of(System.getProperty("nq.paper.emergency.pg.evidence")).resolve(name);
        Files.createDirectories(evidence);
        var control = new JdbcTemplate(new DriverManagerDataSource(maintenance, user, password));
        control.setQueryTimeout(10);
        control.execute("CREATE DATABASE " + name);
        boolean passed = false;
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()) {
            var source = new DriverManagerDataSource(url, user, password);
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(10);
            String run;
            JsonNode original;
            UUID id = UUID.randomUUID();
            try (var first = launch("first", url, user, password, evidence)) {
                String token = login(http, first.port, "continuous-owner");
                String other = login(http, first.port, "continuous-other");
                String viewer = login(http, first.port, "continuous-viewer");
                run = createLegacy(http, first.port, token, first.ready().path("publish").asText());
                ok(send(http, first.port, "POST", base(run) + "/start", token, null));
                assertEquals(400, stop(http, first.port, run, token, null, "manual").statusCode());
                assertEquals(0, count(jdbc, "emergency_stop_events"));
                // 真实独立连接持有原请求锁，证明忙碌分支没有停机或事件副作用。
                try (var lock = source.getConnection()) {
                    lock.setAutoCommit(false);
                    try (var stmt =
                            lock.prepareStatement(
                                    "SELECT pg_advisory_xact_lock(hashtextextended(?,9257))")) {
                        stmt.setString(1, "paper-emergency-stop:" + id);
                        stmt.execute();
                        assertEquals(
                                409, stop(http, first.port, run, token, id, "manual").statusCode());
                        assertEquals("RUNNING", state(jdbc, run));
                        assertEquals(0, count(jdbc, "emergency_stop_events"));
                    } finally {
                        lock.rollback();
                    }
                }
                var left =
                        CompletableFuture.supplyAsync(
                                () -> uncheckedStop(http, first.port, run, token, id));
                var right =
                        CompletableFuture.supplyAsync(
                                () -> uncheckedStop(http, first.port, run, token, id));
                int l = left.get(15, TimeUnit.SECONDS).statusCode(),
                        r = right.get(15, TimeUnit.SECONDS).statusCode();
                assertTrue(l == 200 || r == 200);
                assertTrue(l == 200 || l == 409);
                assertTrue(r == 200 || r == 409);
                original = ok(send(http, first.port, "GET", result(run, id), token, null));
                assertEquals("APPLIED", original.path("status").asText());
                assertEquals("continuous-owner", original.path("triggeredBy").asText());
                assertEquals("es-" + id, original.path("emergencyStopId").asText());
                assertEquals("STOPPED", state(jdbc, run));
                assertEquals(1, count(jdbc, "emergency_stop_events"));
                assertEquals(original, ok(stop(http, first.port, run, token, id, "manual")));
                assertEquals(409, stop(http, first.port, run, token, id, "changed").statusCode());
                assertEquals(
                        404,
                        send(http, first.port, "GET", result(run, id), other, null).statusCode());
                assertEquals(409, stop(http, first.port, run, other, id, "manual").statusCode());
                assertEquals(
                        403,
                        send(http, first.port, "GET", result(run, id), viewer, null).statusCode());
                assertEquals(
                        403,
                        stop(http, first.port, run, viewer, UUID.randomUUID(), "manual")
                                .statusCode());
                assertEquals(
                        404,
                        send(http, first.port, "GET", result("ptr-wrong", id), token, null)
                                .statusCode());
                var failed =
                        ok(
                                stop(
                                        http,
                                        first.port,
                                        run,
                                        token,
                                        UUID.randomUUID(),
                                        "already stopped"));
                assertEquals("FAILED", failed.path("status").asText());
                String rollbackRun =
                        createLegacy(
                                http, first.port, token, first.ready().path("publish").asText());
                ok(send(http, first.port, "POST", base(rollbackRun) + "/start", token, null));
                UUID rollbackId = UUID.randomUUID();
                // 仅在本测试随机库拒绝指定原事件，用真实SQL失败验证停机与事件共同回滚。
                jdbc.execute(
                        "CREATE FUNCTION reject_emergency_fixture() RETURNS trigger LANGUAGE"
                            + " plpgsql AS $$ BEGIN IF NEW.emergency_stop_id='es-"
                                + rollbackId
                                + "' THEN RAISE EXCEPTION 'controlled event failure'; END IF;"
                                + " RETURN NEW; END $$");
                jdbc.execute(
                        "CREATE TRIGGER reject_emergency_fixture BEFORE INSERT ON"
                            + " emergency_stop_events FOR EACH ROW EXECUTE FUNCTION"
                            + " reject_emergency_fixture()");
                try {
                    assertEquals(
                            500,
                            stop(http, first.port, rollbackRun, token, rollbackId, "rollback")
                                    .statusCode());
                    assertEquals("RUNNING", state(jdbc, rollbackRun));
                    assertEquals(
                            0,
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM emergency_stop_events WHERE"
                                        + " emergency_stop_id=?",
                                    Integer.class,
                                    "es-" + rollbackId));
                    assertEquals(
                            404,
                            send(
                                            http,
                                            first.port,
                                            "GET",
                                            result(rollbackRun, rollbackId),
                                            token,
                                            null)
                                    .statusCode());
                } finally {
                    jdbc.execute("DROP TRIGGER reject_emergency_fixture ON emergency_stop_events");
                    jdbc.execute("DROP FUNCTION reject_emergency_fixture()");
                }
                assertEquals(
                        "APPLIED",
                        ok(stop(http, first.port, rollbackRun, token, rollbackId, "rollback"))
                                .path("status")
                                .asText());
                assertEquals(0, count(jdbc, "orders"));
                assertEquals(0, count(jdbc, "trades"));
                assertEquals(0, count(jdbc, "ledger_entries"));
                Files.writeString(evidence.resolve("original.json"), original.toString());
            }
            try (var second = launch("restart", url, user, password, evidence)) {
                String token = login(http, second.port, "continuous-owner");
                assertEquals(
                        original, ok(send(http, second.port, "GET", result(run, id), token, null)));
                int before = count(jdbc, "emergency_stop_events");
                assertEquals(original, ok(stop(http, second.port, run, token, id, "manual")));
                assertEquals(before, count(jdbc, "emergency_stop_events"));
                assertEquals(0, count(jdbc, "orders"));
                assertEquals(0, count(jdbc, "trades"));
                assertEquals(0, count(jdbc, "ledger_entries"));
            }
            Files.writeString(
                    evidence.resolve("result.txt"),
                    "PASS_HTTP_PG33_ORIGINAL_EVENT_LOCK_CONCURRENCY_OWNER_ROLE_INPUT_CONFLICT_ROLLBACK_REAL_JVM_RESTART_ZERO_TRADING_FACTS\n"
                        + "Synthetic research preconditions only; no real provider, LIVE, GUI or"
                        + " installed-release claim.\n");
            passed = true;
        } finally {
            if (passed) control.execute("DROP DATABASE " + name);
        }
    }

    private String createLegacy(HttpClient http, int port, String token, String publish)
            throws Exception {
        return ok(send(
                        http,
                        port,
                        "POST",
                        "/api/paper-trading/runs",
                        token,
                        mapper.createObjectNode()
                                .put("publishId", publish)
                                .put("tradeEnv", "SIM")
                                .put("exchangeCode", "OKX")
                                .put("marketType", "SPOT")
                                .put("symbol", "BTC-USDT")
                                .put("intervalCode", "1h")
                                .put("configSnapshotJson", "{}")))
                .path("paperRunId")
                .asText();
    }

    private HttpResponse<String> uncheckedStop(
            HttpClient http, int port, String run, String token, UUID id) {
        try {
            return stop(http, port, run, token, id, "manual");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private HttpResponse<String> stop(
            HttpClient http, int port, String run, String token, UUID id, String reason)
            throws Exception {
        var req =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:" + port + base(run) + "/emergency-stop"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json");
        if (id != null) req.header("Idempotency-Key", id.toString());
        return http.send(
                req.POST(
                                HttpRequest.BodyPublishers.ofString(
                                        mapper.createObjectNode()
                                                .put("triggerType", "MANUAL")
                                                .put("reason", reason)
                                                .put("triggeredBy", "untrusted-client-name")
                                                .toString()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String base(String run) {
        return "/api/paper-trading/runs/" + run;
    }

    private static String result(String run, UUID id) {
        return base(run) + "/emergency-stop-operations/" + id;
    }

    private static int count(JdbcTemplate jdbc, String table) {
        if (!List.of("emergency_stop_events", "orders", "trades", "ledger_entries").contains(table))
            throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static String state(JdbcTemplate jdbc, String run) {
        return jdbc.queryForObject(
                "SELECT status FROM paper_trading_runs WHERE paper_run_id=?", String.class, run);
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
                        + "emergency"
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
