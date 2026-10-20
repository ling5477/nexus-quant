package com.guidinglight.nexusquant.app.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunIllegalTransitionAuditWriter;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunMaterializationActor;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.service.ShadowRunCreationResultQueryService;
import com.sun.net.httpserver.HttpServer;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.IOException;
import java.net.InetSocketAddress;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 正式创建/结果 GET、真实 PG 快照、响应丢失及两个 JVM 的恢复证明；不访问当前验收库。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class ShadowRunCreationHttpRestartPostgresIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final List<String> FACT_TABLES =
            List.of(
                    "shadow_runs",
                    "shadow_run_events",
                    "shadow_run_snapshots",
                    "shadow_consistency_reports",
                    "strategy_release_admission_state",
                    "paper_trading_runs",
                    "paper_trading_orders",
                    "paper_trading_trades",
                    "orders",
                    "trades",
                    "ledger_entries",
                    "ledger_events",
                    "strategy_sim_decisions",
                    "audit_logs");

    @Test
    void originalCreationSurvivesLostResponseChangedAdmissionAndTwoJvmsWithoutNewSideEffects()
            throws Exception {
        String controlUrl = System.getProperty("nq.postgres.smoke.url", "");
        String user = System.getenv("TASK_SHADOW_CREATION_DB_USER");
        String password = System.getenv("TASK_SHADOW_CREATION_DB_PASSWORD");
        assertTrue(user != null && password != null);
        URI address = URI.create(controlUrl.replaceFirst("^jdbc:", ""));
        assertEquals("127.0.0.1", address.getHost());
        assertEquals(15442, address.getPort());
        assertTrue(address.getQuery() == null && address.getUserInfo() == null);
        String database = "shadow_creation_http_" + UUID.randomUUID().toString().replace("-", "");
        String url = controlUrl.substring(0, controlUrl.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(
                                System.getProperty(
                                        "nq.shadow.creation.http.evidence",
                                        System.getProperty("java.io.tmpdir")))
                        .toAbsolutePath()
                        .resolve(database);
        Files.createDirectories(evidence);
        JdbcTemplate control = jdbc(controlUrl, user, password);
        control.execute("CREATE DATABASE \"" + database + "\"");
        boolean passed = false;
        try (HttpClient http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .proxy(new NoProxy())
                        .build()) {
            JdbcTemplate jdbc = jdbc(url, user, password);
            String command = "shadow-materialization-" + UUID.randomUUID(),
                    lostCommand = "shadow-materialization-" + UUID.randomUUID();
            JsonNode created, recoveredLost, expected, frozenWindow;
            String publish;
            long firstPid;
            try (FixtureProcess process = launch("create", url, user, password, evidence)) {
                firstPid = process.ready().path("pid").asLong();
                publish = process.ready().path("publishRecordId").asText();
                String owner = login(http, process.port, "shadow-creation-owner");
                String other = login(http, process.port, "shadow-creation-other");
                String admin = login(http, process.port, "shadow-creation-admin");
                String viewer = login(http, process.port, "shadow-creation-viewer");
                String lookup = path(publish, true), create = path(publish, false);
                assertEquals(
                        401, send(http, process.port, "GET", lookup, null, command).statusCode());
                assertEquals(
                        403, send(http, process.port, "GET", lookup, viewer, command).statusCode());
                assertEquals(
                        404, send(http, process.port, "GET", lookup, owner, command).statusCode());
                assertEquals(
                        400, send(http, process.port, "GET", lookup, owner, null).statusCode());
                assertEquals(
                        "ELIGIBLE",
                        ok(send(
                                        http,
                                        process.port,
                                        "GET",
                                        "/api/strategy-releases/"
                                                + publish
                                                + "/shadow-admission-preview",
                                        owner,
                                        null))
                                .path("admissionDecision")
                                .asText());
                created = ok(send(http, process.port, "POST", create, owner, command));
                assertEquals("CREATED", created.path("status").asText());
                frozenWindow =
                        mapper.readTree(
                                jdbc.queryForObject(
                                        "SELECT config_snapshot_json::text FROM backtest_runs WHERE backtest_run_id=(SELECT backtest_run_id FROM backtest_publish_records WHERE publish_record_id=?)",
                                        String.class,
                                        publish));
                assertFrozenWindow(http, process.port, owner, created, frozenWindow);
                if (Boolean.getBoolean("nq.shadow.creation.nanosecond-window")) {
                    assertEquals(
                            500,
                            Instant.parse(frozenWindow.path("startTime").asText()).getNano()
                                    % 1000);
                    assertEquals(
                            999,
                            Instant.parse(frozenWindow.path("endTime").asText()).getNano() % 1000);
                }
                JsonNode replay = ok(send(http, process.port, "POST", create, owner, command));
                assertEquals(created.path("shadowRunId"), replay.path("shadowRunId"));
                assertTrue(replay.path("idempotentReplay").asBoolean());
                assertEquals(
                        404, send(http, process.port, "GET", lookup, other, command).statusCode());
                assertEquals(
                        404, send(http, process.port, "GET", lookup, admin, command).statusCode());
                assertEquals(
                        404,
                        send(
                                        http,
                                        process.port,
                                        "GET",
                                        path("another-publish", true),
                                        owner,
                                        command)
                                .statusCode());
                Map<String, String> beforeQueries = fingerprint(jdbc);
                HttpResponse<String> noCache =
                        send(http, process.port, "GET", lookup, owner, command);
                assertTrue(
                        noCache.headers()
                                .firstValue("Cache-Control")
                                .orElse("")
                                .contains("no-store"));
                JsonNode queried = ok(send(http, process.port, "GET", lookup, owner, command));
                assertEquals(created.path("shadowRunId"), queried.path("shadowRunId"));
                assertEquals(created.path("createdAt"), queried.path("createdAt"));
                assertEquals("CREATED", queried.path("creationStatus").asText());
                assertFalse(
                        queried.has("actorId")
                                || queried.has("commandIdentity")
                                || queried.has("idempotentReplay"));
                assertRepeatableReadOnly(jdbc, publish, command);
                assertEquals(beforeQueries, fingerprint(jdbc));

                // 代理等正式 POST 提交成功后丢弃响应；调用方真实收到 IOException，而非伪造一个错误状态。
                AtomicReference<JsonNode> committed = new AtomicReference<>();
                HttpServer drop = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                drop.createContext(
                        "/lost",
                        exchange -> {
                            try {
                                committed.set(
                                        ok(
                                                send(
                                                        http,
                                                        process.port,
                                                        "POST",
                                                        create,
                                                        owner,
                                                        lostCommand)));
                            } catch (Exception failure) {
                                exchange.sendResponseHeaders(502, -1);
                            } finally {
                                exchange.close();
                            }
                        });
                drop.start();
                try {
                    HttpRequest request =
                            HttpRequest.newBuilder(
                                            URI.create(
                                                    "http://127.0.0.1:"
                                                            + drop.getAddress().getPort()
                                                            + "/lost"))
                                    .timeout(Duration.ofSeconds(10))
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build();
                    assertThrows(
                            IOException.class,
                            () -> http.send(request, HttpResponse.BodyHandlers.ofString()));
                    assertTrue(
                            committed.get() != null,
                            "original POST must have committed before response loss");
                } finally {
                    drop.stop(0);
                }
                recoveredLost = ok(send(http, process.port, "GET", lookup, owner, lostCommand));
                assertEquals(
                        committed.get().path("shadowRunId"), recoveredLost.path("shadowRunId"));
                assertNotEquals(created.path("shadowRunId"), recoveredLost.path("shadowRunId"));
                assertEquals(2L, count(jdbc, "shadow_runs"));
                assertEquals(2L, count(jdbc, "shadow_run_events"));

                // 当前诊断状态与原创建状态分别核实，通过既有状态机取消未启动记录，不启动 runner。
                repository(jdbc)
                        .updateStatus(
                                UUID.fromString(created.path("shadowRunId").asText()),
                                ShadowRunStatus.CANCELLED,
                                0,
                                "SYNTHETIC_CANCEL",
                                "isolated creation recovery proof",
                                "state-proof",
                                "state-proof");
                jdbc.update(
                        "UPDATE backtest_eval_reports SET evaluation_status='FAILED' WHERE eval_report_id=(SELECT eval_report_id FROM backtest_publish_records WHERE publish_record_id=?)",
                        publish);
                assertEquals(
                        "BLOCKED",
                        ok(send(
                                        http,
                                        process.port,
                                        "GET",
                                        "/api/strategy-releases/"
                                                + publish
                                                + "/shadow-admission-preview",
                                        owner,
                                        null))
                                .path("admissionDecision")
                                .asText());
                Map<String, String> changed = fingerprint(jdbc);
                expected = ok(send(http, process.port, "GET", lookup, owner, command));
                assertEquals("CREATED", expected.path("creationStatus").asText());
                assertEquals("CANCELLED", expected.path("runStatus").asText());
                assertEquals(changed, fingerprint(jdbc));
                assertNoTrading(jdbc);
                Files.writeString(
                        evidence.resolve("before-restart.json"),
                        mapper.writeValueAsString(
                                Map.of(
                                        "created",
                                        created,
                                        "lostResponseRecovered",
                                        recoveredLost,
                                        "currentResult",
                                        expected,
                                        "fingerprints",
                                        changed,
                                        "firstPid",
                                        firstPid)));
            }
            Path model = evidence.resolve("artifact-root/artifact/model.json");
            assertTrue(model.normalize().startsWith(evidence));
            Files.move(model, model.resolveSibling("model.json.offline"));
            try (FixtureProcess process = launch("reopen", url, user, password, evidence)) {
                assertNotEquals(firstPid, process.ready().path("pid").asLong());
                String owner = login(http, process.port, "shadow-creation-owner");
                String other = login(http, process.port, "shadow-creation-other");
                assertFrozenWindow(http, process.port, owner, created, frozenWindow);
                Map<String, String> before = fingerprint(jdbc);
                JsonNode after =
                        ok(send(http, process.port, "GET", path(publish, true), owner, command));
                assertEquals(expected, after);
                assertEquals(
                        recoveredLost,
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "GET",
                                        path(publish, true),
                                        owner,
                                        lostCommand)));
                assertEquals(
                        404,
                        send(http, process.port, "GET", path(publish, true), other, command)
                                .statusCode());
                assertEquals(before, fingerprint(jdbc));
                assertEquals(
                        422,
                        send(
                                        http,
                                        process.port,
                                        "POST",
                                        path(publish, false),
                                        owner,
                                        "shadow-materialization-" + UUID.randomUUID())
                                .statusCode());
                assertEquals(2L, count(jdbc, "shadow_runs"));
                // 人工追加损坏审计只存在于随机测试库；查询必须发现重复，不能 LIMIT 1 任取成功。
                jdbc.update(
                        "INSERT INTO shadow_run_events(id,shadow_run_id,event_type,from_status,to_status,reason_code,message,metadata,request_id,trace_id,created_at) "
                                + "SELECT ?,shadow_run_id,event_type,from_status,to_status,reason_code,message,metadata,request_id,trace_id,created_at FROM shadow_run_events "
                                + "WHERE shadow_run_id=? AND event_type='CREATED' ORDER BY id LIMIT 1",
                        UUID.randomUUID(),
                        UUID.fromString(created.path("shadowRunId").asText()));
                Map<String, String> damaged = fingerprint(jdbc);
                HttpResponse<String> unavailable =
                        send(http, process.port, "GET", path(publish, true), owner, command);
                assertEquals(503, unavailable.statusCode());
                assertEquals(
                        "SHADOW_CREATION_RESULT_UNAVAILABLE",
                        mapper.readTree(unavailable.body()).path("code").asText());
                assertEquals(damaged, fingerprint(jdbc));
                assertEquals(
                        2,
                        repository(jdbc)
                                .listCreationEvents(
                                        UUID.fromString(created.path("shadowRunId").asText()))
                                .size());
                assertNoTrading(jdbc);
                Files.writeString(
                        evidence.resolve("proof.json"),
                        mapper.writeValueAsString(
                                Map.of(
                                        "status",
                                        "PASS_SCOPED_REAL_HTTP_PG_TWO_JVMS",
                                        "database",
                                        database,
                                        "firstPid",
                                        firstPid,
                                        "secondPid",
                                        process.ready().path("pid").asLong(),
                                        "runs",
                                        count(jdbc, "shadow_runs"),
                                        "queryFingerprintsPreserved",
                                        true,
                                        "responseLostThenOriginalGetRecovered",
                                        true,
                                        "missingArtifactGetRecovered",
                                        true,
                                        "duplicateAuditUnavailable",
                                        true,
                                        "noTrading",
                                        true)));
            }
            passed = true;
        } finally {
            // 失败库留作归因；成功仅删除本测试精确随机数据库，不涉及原验收事实。
            if (passed) {
                assertTrue(database.matches("shadow_creation_http_[a-f0-9]{32}"));
                control.queryForList(
                        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=? AND pid<>pg_backend_pid()",
                        database);
                control.execute("DROP DATABASE \"" + database + "\"");
            }
        }
    }

    private void assertRepeatableReadOnly(JdbcTemplate jdbc, String publish, String command) {
        List<String> observations = new ArrayList<>();
        ShadowRunFactRepository delegate = repository(jdbc);
        ProxyFactory observingFactory = new ProxyFactory(delegate);
        observingFactory.addAdvice(
                (MethodInterceptor)
                        invocation -> {
                            assertTrue(
                                    List.of("findByIdempotencyKey", "listCreationEvents")
                                            .contains(invocation.getMethod().getName()));
                            observations.add(
                                    jdbc.queryForObject("SHOW transaction_isolation", String.class)
                                            + ":"
                                            + jdbc.queryForObject(
                                                    "SHOW transaction_read_only", String.class));
                            return invocation.proceed();
                        });
        ShadowRunFactRepository observing = (ShadowRunFactRepository) observingFactory.getProxy();
        ProxyFactory factory = new ProxyFactory(new ShadowRunCreationResultQueryService(observing));
        factory.addAdvice(
                new TransactionInterceptor(
                        new DataSourceTransactionManager(jdbc.getDataSource()),
                        new AnnotationTransactionAttributeSource()));
        ShadowRunCreationResultQueryService service =
                (ShadowRunCreationResultQueryService) factory.getProxy();
        Long actor =
                jdbc.queryForObject(
                        "SELECT (metadata->>'actorId')::bigint FROM shadow_run_events WHERE event_type='CREATED'",
                        Long.class);
        assertTrue(
                service.query(
                                publish,
                                command,
                                new ShadowRunMaterializationActor(actor, List.of("OPERATOR")))
                        .isPresent());
        assertEquals(List.of("repeatable read:on", "repeatable read:on"), observations);
    }

    private JdbcShadowRunFactRepository repository(JdbcTemplate jdbc) {
        DataSourceTransactionManager manager =
                new DataSourceTransactionManager(jdbc.getDataSource());
        return new JdbcShadowRunFactRepository(
                jdbc,
                mapper,
                new JdbcShadowRunIllegalTransitionAuditWriter(jdbc, mapper, manager),
                new JdbcAdmissionMutationCoordinator(jdbc, manager, 256));
    }

    private static JdbcTemplate jdbc(String url, String user, String password) {
        return new JdbcTemplate(
                new DriverManagerDataSource(
                        url + "?connectTimeout=5&socketTimeout=30", user, password));
    }

    private static long count(JdbcTemplate jdbc, String table) {
        assertTrue(FACT_TABLES.contains(table));
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static Map<String, String> fingerprint(JdbcTemplate jdbc) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : FACT_TABLES)
            result.put(
                    table,
                    jdbc.queryForObject(
                            "SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,'|' ORDER BY to_jsonb(t)::text),'')) FROM "
                                    + table
                                    + " t",
                            String.class));
        return result;
    }

    private static void assertNoTrading(JdbcTemplate jdbc) {
        for (String table :
                List.of(
                        "orders",
                        "trades",
                        "ledger_entries",
                        "ledger_events",
                        "paper_trading_orders",
                        "paper_trading_trades",
                        "strategy_sim_decisions")) assertEquals(0L, count(jdbc, table), table);
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM shadow_runs WHERE started_at IS NOT NULL OR NOT(no_order_submission AND no_credential_access AND no_private_endpoint AND no_ledger_mutation AND no_account_mutation AND no_external_private_io)",
                        Long.class));
    }

    private static String path(String publish, boolean lookup) {
        return "/api/strategy-releases/"
                + publish
                + (lookup ? "/shadow-run-creation-result" : "/shadow-runs");
    }

    private String login(HttpClient http, int port, String username) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "username",
                                                        username,
                                                        "password",
                                                        ShadowRunCreationHttpFixtureMain
                                                                .PASSWORD))))
                        .build();
        JsonNode value =
                ok(http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        assertFalse(value.path("accessToken").asText().isBlank());
        return value.path("accessToken").asText();
    }

    private static HttpResponse<String> send(
            HttpClient http, int port, String method, String path, String token, String command)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(8));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (command != null) builder.header("Idempotency-Key", command);
        return http.send(
                builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        // 登录响应可能含 JWT，只报告状态码，断言不得输出原认证响应。
        assertEquals(
                200,
                response.statusCode(),
                "safe error code: "
                        + (response.statusCode() == 200
                                ? "NONE"
                                : mapper.readTree(response.body()).path("code").asText()));
        return mapper.readTree(response.body());
    }

    /** 两个不同 JVM 均经正式详情 GET 核对原冻结时刻，不用数据库近似值当作期望。 */
    private void assertFrozenWindow(
            HttpClient http, int port, String owner, JsonNode created, JsonNode frozen)
            throws Exception {
        JsonNode detail =
                ok(
                        send(
                                http,
                                port,
                                "GET",
                                "/api/shadow-runs/" + created.path("shadowRunId").asText(),
                                owner,
                                null));
        assertEquals(
                Instant.parse(frozen.path("startTime").asText()),
                Instant.parse(detail.path("windowStart").asText()));
        assertEquals(
                Instant.parse(frozen.path("endTime").asText()),
                Instant.parse(detail.path("windowEnd").asText()));
    }

    private FixtureProcess launch(
            String mode, String url, String user, String password, Path evidence) throws Exception {
        Path args = evidence.resolve(mode + ".args");
        String cp =
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path"));
        Files.writeString(
                args,
                "-Duser.timezone=UTC\n-cp\n\""
                        + cp.replace("\\", "\\\\").replace("\"", "\\\"")
                        + "\"\n"
                        + ShadowRunCreationHttpFixtureMain.class.getName()
                        + "\n"
                        + mode
                        + "\n"
                        + quoted(evidence.resolve(mode + ".ready.json"))
                        + "\n"
                        + quoted(evidence.resolve(mode + ".stop"))
                        + "\n");
        ProcessBuilder builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "@" + args);
        builder.environment().put("TASK_SHADOW_CREATION_DB_URL", url);
        builder.environment().put("TASK_SHADOW_CREATION_DB_USER", user);
        builder.environment().put("TASK_SHADOW_CREATION_DB_PASSWORD", password);
        builder.environment()
                .put(
                        "TASK_SHADOW_CREATION_NANOSECOND_WINDOW",
                        Boolean.toString(
                                Boolean.getBoolean("nq.shadow.creation.nanosecond-window")));
        builder.redirectErrorStream(true).redirectOutput(evidence.resolve(mode + ".log").toFile());
        FixtureProcess process =
                new FixtureProcess(builder.start(), evidence, mode, user, password);
        try {
            process.awaitReady();
            return process;
        } catch (Exception | AssertionError failure) {
            process.close();
            throw failure;
        }
    }

    private static String quoted(Path path) {
        return "\"" + path.toString().replace("\\", "\\\\") + "\"";
    }

    private final class FixtureProcess implements AutoCloseable {
        private final Process process;
        private final Path evidence;
        private final String mode, user, password;
        private int port;

        FixtureProcess(Process process, Path evidence, String mode, String user, String password) {
            this.process = process;
            this.evidence = evidence;
            this.mode = mode;
            this.user = user;
            this.password = password;
        }

        JsonNode ready() throws Exception {
            return mapper.readTree(Files.readString(evidence.resolve(mode + ".ready.json")));
        }

        void awaitReady() throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(55).toNanos();
            while (!Files.exists(evidence.resolve(mode + ".ready.json"))
                    && process.isAlive()
                    && System.nanoTime() < deadline) Thread.sleep(100);
            assertTrue(
                    Files.exists(evidence.resolve(mode + ".ready.json")),
                    "bounded fixture did not become ready: " + mode);
            port = ready().path("port").asInt();
            assertTrue(port > 0);
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
                                .replace(password, "[REDACTED]")
                                .replace(user, "[REDACTED]")
                                .replaceAll(
                                        "(?m)(Using generated security password:\\s*)\\S+",
                                        "$1[REDACTED]"));
            assertTrue(stopped, "owned fixture needed forced termination: " + mode);
            assertEquals(0, process.exitValue(), "fixture exit: " + mode);
        }
    }

    /** localhost 请求不继承系统代理，合成认证信息不会发到外部代理。 */
    private static final class NoProxy extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {}
    }
}
