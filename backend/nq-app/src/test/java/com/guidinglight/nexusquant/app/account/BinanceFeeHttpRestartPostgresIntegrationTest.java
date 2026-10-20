package com.guidinglight.nexusquant.app.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeSourceIdentity;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcAccountFeeRepository;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 真正跨JVM的Servlet/JWT/PG证明；关闭来源后只能恢复原观察，不能伪造新的同步成功。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class BinanceFeeHttpRestartPostgresIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hmacObservationSurvivesProcessRestartWithoutPrivateSource() throws Exception {
        prove("observe-hmac");
    }

    @Test
    void ed25519ObservationSurvivesProcessRestartWithoutPrivateSource() throws Exception {
        prove("observe-ed25519");
    }

    /** 控制连接只创建本次随机库；业务断言读取已提交行，绝不写入现有验收库。 */
    private void prove(String mode) throws Exception {
        String baseUrl = System.getProperty("nq.postgres.smoke.url", "");
        String user = System.getProperty("nq.postgres.smoke.user", "");
        String password = System.getProperty("nq.postgres.smoke.password", "");
        URI controlUri = URI.create(baseUrl.replaceFirst("^jdbc:", ""));
        assertEquals("127.0.0.1", controlUri.getHost());
        assertTrue(controlUri.getPort() > 0 && controlUri.getPath().matches("/[A-Za-z0-9_]+"));
        assertTrue(controlUri.getRawQuery() == null && controlUri.getFragment() == null);
        assertFalse(user.isBlank());
        assertFalse(password.isBlank());
        String database = "binance_fee_http_" + UUID.randomUUID().toString().replace("-", "");
        String url = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(
                                System.getProperty(
                                        "nq.fee.http.evidence",
                                        System.getProperty("java.io.tmpdir")))
                        .toAbsolutePath()
                        .resolve(database);
        Files.createDirectories(evidence);
        var control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                baseUrl + "?connectTimeout=5&socketTimeout=30", user, password));
        control.execute("CREATE DATABASE \"" + database + "\"");
        try (var http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .proxy(new DirectProxy())
                        .build()) {
            var jdbc =
                    new JdbcTemplate(
                            new DriverManagerDataSource(
                                    url + "?connectTimeout=5&socketTimeout=30", user, password));
            UUID operation = UUID.randomUUID();
            JsonNode first;
            JsonNode identity;
            String body;
            long firstPid;
            try (var process = launch(mode, url, user, password, evidence)) {
                identity = process.ready();
                firstPid = identity.path("pid").asLong();
                long account = identity.path("account").asLong(),
                        credential = identity.path("credential").asLong();
                String token = login(http, process.port, "http-fee-owner");
                body =
                        mapper.writeValueAsString(
                                Map.of("credentialId", credential, "symbol", "BTC-USDT"));
                String path = path(account, operation);
                assertEquals(401, send(http, process.port, "GET", path, null, null).statusCode());
                String viewer = login(http, process.port, "http-fee-viewer");
                assertEquals(
                        403, send(http, process.port, "POST", path, viewer, body).statusCode());
                String other = login(http, process.port, "http-fee-other");
                assertEquals(404, send(http, process.port, "POST", path, other, body).statusCode());
                assertEquals(
                        0L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM account_fee_observations", Long.class));
                first = ok(send(http, process.port, "POST", path, token, body));
                assertEquals("SUCCEEDED", first.path("status").asText());
                assertEquals("BINANCE_PRIVATE_SPOT_COMMISSION", first.path("source").asText());
                assertEquals("BINANCE_POSITIVE_COST_V1", first.path("rateConvention").asText());
                for (String absent :
                        List.of(
                                "makerProviderRate",
                                "takerProviderRate",
                                "makerCostRate",
                                "takerCostRate",
                                "tier")) assertTrue(first.path(absent).isNull(), absent);
                var terms = first.path("commissionTerms");
                for (String component : List.of("standard", "tax", "special")) {
                    assertEquals(4, terms.path(component).size());
                    for (String field : List.of("maker", "taker", "buyer", "seller"))
                        assertTrue(terms.path(component).path(field).isTextual());
                }
                assertEquals("-0.000000000001", terms.path("standard").path("maker").asText());
                assertEquals("0.001000000000", terms.path("standard").path("taker").asText());
                assertEquals("0.000010000000", terms.path("special").path("taker").asText());
                assertEquals("BNB", terms.path("discount").path("asset").asText());
                assertTrue(terms.path("discount").path("enabledForAccount").asBoolean());
                assertTrue(first.path("sourceSha256").asText().matches("[0-9a-f]{64}"));
                assertFalse(first.has("inputFingerprint"));
                assertFalse(first.has("ownerUserId"));
                assertEquals(first, ok(send(http, process.port, "POST", path, token, body)));
                assertEquals(first, ok(send(http, process.port, "GET", path, token, null)));
                // 同UUID输入变化属于冲突，不能暗中重新读取、覆盖原观察或生成新身份。
                String different =
                        mapper.writeValueAsString(
                                Map.of("credentialId", credential, "symbol", "ETH-USDT"));
                assertEquals(
                        409, send(http, process.port, "POST", path, token, different).statusCode());
                assertEquals(404, send(http, process.port, "GET", path, other, null).statusCode());
                persisted(jdbc, identity, operation, first);
            }
            assertTrue(
                    Files.readString(evidence.resolve(mode + ".log"))
                            .contains("providerGets=3 signatures=2"));
            try (var reopened = launch("reopen", url, user, password, evidence)) {
                assertNotEquals(firstPid, reopened.ready().path("pid").asLong());
                long account = identity.path("account").asLong();
                String token = login(http, reopened.port, "http-fee-owner");
                assertEquals(
                        first,
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        path(account, operation),
                                        token,
                                        null)));
                assertEquals(
                        first,
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "POST",
                                        path(account, operation),
                                        token,
                                        body)));
                var history =
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        "/api/exchange-accounts/"
                                                + account
                                                + "/fees?symbol=BTC-USDT",
                                        token,
                                        null));
                assertFalse(history.path("synchronizationAvailable").asBoolean());
                assertEquals(first, history.path("items").get(0));
                assertEquals(
                        409,
                        send(
                                        http,
                                        reopened.port,
                                        "POST",
                                        path(account, UUID.randomUUID()),
                                        token,
                                        body)
                                .statusCode());
                persisted(jdbc, identity, operation, first);
            }
            assertTrue(
                    Files.readString(evidence.resolve("reopen.log"))
                            .contains("providerGets=0 signatures=0"));
            Files.writeString(
                    evidence.resolve("proof.json"),
                    mapper.writeValueAsString(
                            Map.of(
                                    "result",
                                    "PASS_SCOPED",
                                    "mode",
                                    mode,
                                    "database",
                                    database,
                                    "operation",
                                    operation.toString(),
                                    "observation",
                                    first.path("observationId").asText(),
                                    "sourceSha256",
                                    first.path("sourceSha256").asText(),
                                    "firstPid",
                                    firstPid,
                                    "reopenProviderGets",
                                    0,
                                    "economicRows",
                                    0)));
        } finally {
            // 仅清理本测试创建的随机库，失败进程的日志与独立身份文件保留。
            assertTrue(database.matches("binance_fee_http_[a-f0-9]{32}"));
            control.queryForList(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=? AND pid<>pg_backend_pid()",
                    database);
            control.execute("DROP DATABASE \"" + database + "\"");
        }
    }

    /** 重建repository只是独立连接重读；跨进程证据来自前后两个真实Servlet JVM。 */
    private void persisted(
            JdbcTemplate jdbc, JsonNode identity, UUID operation, JsonNode response) {
        var row =
                new JdbcAccountFeeRepository(jdbc)
                        .findOperation(identity.path("owner").asLong(), operation)
                        .orElseThrow();
        assertEquals(response.path("observationId").asText(), row.observationId().toString());
        assertEquals(response.path("sourceSha256").asText(), AccountFeeSourceIdentity.digest(row));
        assertEquals(
                response.path("commissionTerms"), row.quote().commissionTerms().snapshot(mapper));
        assertEquals(
                1L,
                jdbc.queryForObject("SELECT count(*) FROM account_fee_observations", Long.class));
        for (String table : List.of("orders", "trades", "ledger_entries"))
            assertEquals(
                    0L, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class), table);
        assertEquals(
                "ENGAGED",
                jdbc.queryForObject(
                        "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                        String.class));
    }

    private String login(HttpClient http, int port, String username) throws Exception {
        var body =
                mapper.writeValueAsString(
                        Map.of(
                                "username",
                                username,
                                "password",
                                BinanceFeeHttpFixtureMain.PASSWORD));
        var result = ok(send(http, port, "POST", "/api/auth/login", null, body));
        assertFalse(result.path("accessToken").asText().isBlank());
        return result.path("accessToken").asText();
    }

    private HttpResponse<String> send(
            HttpClient http, int port, String method, String path, String token, String body)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(8));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return http.send(
                request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        // 认证响应可能带JWT；失败只报告状态码，禁止把完整body放入断言输出。
        assertEquals(200, response.statusCode());
        return mapper.readTree(response.body());
    }

    private static String path(long account, UUID operation) {
        return "/api/exchange-accounts/" + account + "/fees/operations/" + operation;
    }

    /** JVM参数文件避免Windows命令行长度限制；只含classpath和合成配置，数据库连接材料经环境传递。 */
    private FixtureProcess launch(
            String mode, String url, String user, String password, Path evidence) throws Exception {
        Path args = evidence.resolve(mode + ".args");
        String cp =
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path"));
        String escaped = cp.replace("\\", "\\\\").replace("\"", "\\\"");
        // main参数顺序固定；就绪/停止文件路径同样以参数文件传入，避免shell插值。
        Files.writeString(
                args,
                "-Duser.timezone=UTC\n-cp\n\""
                        + escaped
                        + "\"\n"
                        + BinanceFeeHttpFixtureMain.class.getName()
                        + "\n"
                        + mode
                        + "\n"
                        + quoted(evidence.resolve(mode + ".ready.json"))
                        + "\n"
                        + quoted(evidence.resolve(mode + ".stop"))
                        + "\n");
        var builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "@" + args);
        builder.environment().put("TASK_BINANCE_FEE_HTTP_DB_URL", url);
        builder.environment().put("TASK_BINANCE_FEE_HTTP_DB_USER", user);
        builder.environment().put("TASK_BINANCE_FEE_HTTP_DB_PASSWORD", password);
        builder.redirectErrorStream(true).redirectOutput(evidence.resolve(mode + ".log").toFile());
        var fixture = new FixtureProcess(builder.start(), evidence, mode, user, password);
        try {
            fixture.awaitReady();
            return fixture;
        } catch (Exception | AssertionError failure) {
            fixture.close();
            throw failure;
        }
    }

    private static String quoted(Path path) {
        return "\"" + path.toString().replace("\\", "\\\\") + "\"";
    }

    /** 有界启动/关闭仅管理本测试启动的子进程；保留日志且擦除连接材料。 */
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
                    "Servlet fixture did not become ready: " + mode);
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
            if (Files.exists(log)) {
                String content =
                        Files.readString(log)
                                .replace(password, "[REDACTED]")
                                .replace(user, "[REDACTED]")
                                .replaceAll(
                                        "(?m)(Using generated security password:\\s*)\\S+",
                                        "$1[REDACTED]");
                Files.writeString(log, content);
            }
            assertTrue(stopped, "Servlet fixture required forced termination: " + mode);
            assertEquals(0, process.exitValue(), "Servlet fixture exit: " + mode);
        }
    }

    /** localhost请求不继承系统代理，保证合成材料不会外发。 */
    private static final class DirectProxy extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socket, IOException failure) {}
    }
}
