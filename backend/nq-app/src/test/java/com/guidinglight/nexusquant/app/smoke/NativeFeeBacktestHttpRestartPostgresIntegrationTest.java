package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
class NativeFeeBacktestHttpRestartPostgresIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void positiveNativeFeesSurviveRestartAndDeferredExecution() throws Exception {
        prove("positive");
    }

    @Test
    void zeroNativeFeesSurviveRestartAndDeferredExecution() throws Exception {
        prove("zero");
    }

    @Test
    void negativeNativeFeesSurviveRestartAndDeferredExecution() throws Exception {
        prove("negative");
    }

    /** 分钟正收益的不可表示年化原因必须与真实收益一起落库、由HTTP读取并在另一个JVM保持。 */
    @Test
    void minutePositiveReturnPreservesAnnualizedReasonAcrossRestart() throws Exception {
        prove("minute-gain");
    }

    /** 两个独立JVM经真实Servlet/JWT执行；来源关闭后只消费原PG观察，不用新观察或重写历史快照。 */
    private void prove(String mode) throws Exception {
        String baseUrl = System.getProperty("nq.postgres.smoke.url", "");
        String user = System.getProperty("nq.postgres.smoke.user", ""),
                password = System.getProperty("nq.postgres.smoke.password", "");
        URI controlUri = URI.create(baseUrl.replaceFirst("^jdbc:", ""));
        assertEquals("127.0.0.1", controlUri.getHost());
        assertTrue(controlUri.getPort() > 0 && controlUri.getPath().matches("/[A-Za-z0-9_]+"));
        assertTrue(controlUri.getRawQuery() == null && controlUri.getFragment() == null);
        assertFalse(user.isBlank());
        assertFalse(password.isBlank());
        String database = "native_backtest_http_" + UUID.randomUUID().toString().replace("-", "");
        String url = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(
                                System.getProperty(
                                        "nq.native.backtest.http.evidence",
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
            JsonNode identity, observation, run, evaluation, trades, pnl, positions, deferred;
            long firstPid;
            try (var process = launch(mode, url, user, password, evidence)) {
                identity = process.ready();
                firstPid = identity.path("pid").asLong();
                String token = login(http, process.port, "native-http-owner"),
                        viewer = login(http, process.port, "native-http-viewer");
                String other = login(http, process.port, "native-http-other");
                String observationPath = path(identity.path("account").asLong(), UUID.randomUUID());
                String observationBody =
                        mapper.writeValueAsString(
                                Map.of(
                                        "credentialId",
                                        identity.path("credential").asLong(),
                                        "symbol",
                                        "BTC-USDT"));
                assertEquals(
                        401,
                        send(http, process.port, "POST", observationPath, null, observationBody)
                                .statusCode());
                assertEquals(
                        403,
                        send(http, process.port, "POST", observationPath, viewer, observationBody)
                                .statusCode());
                observation =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "POST",
                                        observationPath,
                                        token,
                                        observationBody));
                assertEquals("SUCCEEDED", observation.path("status").asText());
                var spec =
                        mapper.createObjectNode()
                                .put("costSource", "CURRENT_ACCOUNT_OBSERVATION")
                                .put("feeExchangeCode", "BINANCE")
                                .put("feePaymentPolicy", "RECEIVED_ASSET")
                                .put("feeObservationId", observation.path("observationId").asText())
                                .put("feeAccountId", identity.path("account").asText())
                                .put("feeTradeEnv", "LIVE")
                                .put("feeSymbol", "BTC-USDT")
                                .put("quantityStep", "0.0001")
                                .put("priceTick", "0.01")
                                .put("minimumQuantity", "0.0001")
                                .put("minimumNotional", "5")
                                .put("slippageBps", "0");
                String body =
                        mapper.writeValueAsString(
                                Map.of(
                                        "researchConfigId",
                                        identity.path("research").asText(),
                                        "name",
                                        "Synthetic native HTTP " + mode,
                                        "description",
                                        "isolated synthetic proof",
                                        "startTime",
                                        "2026-09-25T00:00:00Z",
                                        "endTime",
                                        "2026-09-25T00:07:59Z",
                                        "initialCapital",
                                        100,
                                        "executionSpec",
                                        spec.toString(),
                                        "evaluationSpec",
                                        "{}"));
                assertEquals(
                        403,
                        send(http, process.port, "POST", "/api/backtest-configs", viewer, body)
                                .statusCode());
                var config =
                        ok(send(http, process.port, "POST", "/api/backtest-configs", token, body));
                String configPath =
                        "/api/backtest-configs/" + config.path("backtestConfigId").asText();
                ok(
                        send(
                                http,
                                process.port,
                                "PATCH",
                                configPath + "/dataset",
                                token,
                                mapper.writeValueAsString(
                                        Map.of("datasetId", identity.path("dataset").asText()))));
                ok(
                        send(
                                http,
                                process.port,
                                "PATCH",
                                configPath + "/strategy-version",
                                token,
                                mapper.writeValueAsString(
                                        Map.of(
                                                "strategyVersionId",
                                                identity.path("strategyVersion").asText()))));
                String createRun =
                        mapper.writeValueAsString(
                                Map.of(
                                        "backtestConfigId",
                                        config.path("backtestConfigId").asText()));
                run = ok(send(http, process.port, "POST", "/api/backtest-runs", token, createRun));
                deferred =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "POST",
                                        "/api/backtest-runs",
                                        token,
                                        createRun));
                assertNotEquals(run.path("backtestRunId"), deferred.path("backtestRunId"));
                String runPath = runPath(run);
                assertEquals(
                        403,
                        send(http, process.port, "POST", runPath + "/start", viewer, null)
                                .statusCode());
                assertDenied(send(http, process.port, "POST", runPath + "/start", other, null));
                run = ok(send(http, process.port, "POST", runPath + "/start", token, null));
                assertEquals("SUCCEEDED", run.path("status").asText());
                evaluation =
                        ok(send(http, process.port, "POST", runPath + "/evaluate", token, null));
                assertEquals("SUCCEEDED", evaluation.path("evaluationStatus").asText());
                // 以已提交的同进程GET作为恢复基准；POST返回的内存JSON尚未经过JSONB规范化及PG微秒存储。
                evaluation =
                        ok(send(http, process.port, "GET", runPath + "/evaluation", token, null));
                if (mode.equals("minute-gain")) {
                    assertTrue(number(evaluation, "netPnl").signum() > 0);
                    assertTrue(evaluation.path("annualizedReturn").isNull());
                    assertEquals(
                            "OUT_OF_RANGE",
                            mapper.readTree(evaluation.path("metricsJson").asText())
                                    .path("annualizedReturnStatus")
                                    .asText());
                }
                deferred = ok(send(http, process.port, "GET", runPath(deferred), token, null));
                run = ok(send(http, process.port, "GET", runPath, token, null));
                trades = ok(send(http, process.port, "GET", runPath + "/sim-trades", token, null));
                pnl = ok(send(http, process.port, "GET", runPath + "/pnl-snapshots", token, null));
                positions =
                        ok(
                                send(
                                        http,
                                        process.port,
                                        "GET",
                                        runPath + "/sim-positions",
                                        token,
                                        null));
                economics(jdbc, run, trades, identity.path("signed").asText(), 1);
                assertEquals(
                        409,
                        send(http, process.port, "POST", runPath + "/start", token, null)
                                .statusCode());
                assertEquals(
                        trades,
                        ok(send(http, process.port, "GET", runPath + "/sim-trades", token, null)));
            }
            assertTrue(
                    Files.readString(evidence.resolve(mode + ".log")).contains("providerGets=3"));
            try (var reopened = launch("reopen", url, user, password, evidence)) {
                assertNotEquals(firstPid, reopened.ready().path("pid").asLong());
                String token = login(http, reopened.port, "native-http-owner"),
                        other = login(http, reopened.port, "native-http-other");
                String runPath = runPath(run);
                assertEquals(run, ok(send(http, reopened.port, "GET", runPath, token, null)));
                assertEquals(
                        evaluation,
                        ok(send(http, reopened.port, "GET", runPath + "/evaluation", token, null)));
                assertEquals(
                        trades,
                        ok(send(http, reopened.port, "GET", runPath + "/sim-trades", token, null)));
                assertEquals(
                        pnl,
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        runPath + "/pnl-snapshots",
                                        token,
                                        null)));
                assertEquals(
                        positions,
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        runPath + "/sim-positions",
                                        token,
                                        null)));
                assertDenied(
                        send(http, reopened.port, "GET", runPath + "/sim-trades", other, null));
                assertDenied(send(http, reopened.port, "POST", runPath + "/evaluate", other, null));
                assertEquals(
                        409,
                        send(http, reopened.port, "POST", runPath + "/start", token, null)
                                .statusCode());
                economics(jdbc, run, trades, identity.path("signed").asText(), 1);
                // 重启后才执行预先冻结的第二run；它必须以原观察恢复模型，而不是以来源关闭为由替换费率。
                var resumed =
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "POST",
                                        runPath(deferred) + "/start",
                                        token,
                                        null));
                assertEquals("SUCCEEDED", resumed.path("status").asText());
                var resumedEvaluation =
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "POST",
                                        runPath(deferred) + "/evaluate",
                                        token,
                                        null));
                assertEquals("SUCCEEDED", resumedEvaluation.path("evaluationStatus").asText());
                var resumedTrades =
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        runPath(deferred) + "/sim-trades",
                                        token,
                                        null));
                economics(jdbc, resumed, resumedTrades, identity.path("signed").asText(), 2);
                // 两次创建各自拥有冻结时点；恢复必须保留第二run原快照，同时引用相同正式观察身份。
                assertEquals(
                        deferred.path("configSnapshotJson"), resumed.path("configSnapshotJson"));
                var firstSnapshot =
                        mapper.readTree(
                                mapper.readTree(run.path("configSnapshotJson").asText())
                                        .path("executionSpec")
                                        .path("feeSourceSnapshotJson")
                                        .asText());
                var resumedSnapshot =
                        mapper.readTree(
                                mapper.readTree(resumed.path("configSnapshotJson").asText())
                                        .path("executionSpec")
                                        .path("feeSourceSnapshotJson")
                                        .asText());
                for (String field :
                        List.of(
                                "observationId",
                                "sourceSha256",
                                "buyReceivedAssetRate",
                                "sellReceivedAssetRate",
                                "commissionTerms")) {
                    assertTrue(firstSnapshot.hasNonNull(field), field);
                    assertEquals(firstSnapshot.path(field), resumedSnapshot.path(field));
                }
                assertEquals(
                        trades,
                        ok(send(http, reopened.port, "GET", runPath + "/sim-trades", token, null)));
                var history =
                        ok(
                                send(
                                        http,
                                        reopened.port,
                                        "GET",
                                        "/api/exchange-accounts/"
                                                + identity.path("account").asText()
                                                + "/fees?symbol=BTC-USDT",
                                        token,
                                        null));
                assertFalse(history.path("synchronizationAvailable").asBoolean());
                assertEquals(observation, history.path("items").get(0));
                String observationBody =
                        mapper.writeValueAsString(
                                Map.of(
                                        "credentialId",
                                        identity.path("credential").asLong(),
                                        "symbol",
                                        "BTC-USDT"));
                assertEquals(
                        409,
                        send(
                                        http,
                                        reopened.port,
                                        "POST",
                                        path(identity.path("account").asLong(), UUID.randomUUID()),
                                        token,
                                        observationBody)
                                .statusCode());
                assertEquals(
                        1L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM account_fee_observations", Long.class));
                // 可选GUI检查复用本测试的真实Servlet；等待有界，完成文件只控制本次子进程，不改金融事实。
                if ((mode.equals("negative") || mode.equals("minute-gain"))
                        && Boolean.getBoolean("nq.native.backtest.ui")) {
                    var legacy = legacyForGui(http, reopened.port, token, identity);
                    Path guiReady = evidence.getParent().resolve("native-backtest-ui-ready.json");
                    Path guiDone = evidence.getParent().resolve("native-backtest-ui.done");
                    assertFalse(Files.exists(guiReady));
                    assertFalse(Files.exists(guiDone));
                    Files.writeString(
                            guiReady,
                            mapper.writeValueAsString(
                                    Map.of(
                                            "port",
                                            reopened.port,
                                            "database",
                                            database,
                                            "configId",
                                            run.path("backtestConfigId").asText(),
                                            "runId",
                                            run.path("backtestRunId").asText(),
                                            "datasetId",
                                            identity.path("dataset").asText(),
                                            "pid",
                                            reopened.ready().path("pid").asLong(),
                                            "source",
                                            "REAL_SERVLET_SYNTHETIC_LOOPBACK_SOURCE_DISABLED_AFTER_RESTART",
                                            "legacyConfigId",
                                            legacy.path("backtestConfigId").asText(),
                                            "legacyRunId",
                                            legacy.path("backtestRunId").asText())));
                    long deadline = System.nanoTime() + Duration.ofMinutes(25).toNanos();
                    while (!Files.exists(guiDone) && System.nanoTime() < deadline)
                        Thread.sleep(100);
                    assertTrue(Files.exists(guiDone), "bounded GUI observation did not complete");
                    assertEquals(
                            trades,
                            ok(
                                    send(
                                            http,
                                            reopened.port,
                                            "GET",
                                            runPath + "/sim-trades",
                                            token,
                                            null)));
                    economics(jdbc, resumed, resumedTrades, identity.path("signed").asText(), 3);
                }
            }
            assertTrue(Files.readString(evidence.resolve("reopen.log")).contains("providerGets=0"));
            Files.writeString(
                    evidence.resolve("proof.json"),
                    mapper.writeValueAsString(
                            Map.of(
                                    "result",
                                    "PASS_SCOPED",
                                    "mode",
                                    mode,
                                    "firstPid",
                                    firstPid,
                                    "database",
                                    database,
                                    "run",
                                    run.path("backtestRunId").asText(),
                                    "deferredRun",
                                    deferred.path("backtestRunId").asText(),
                                    "reopenProviderGets",
                                    0,
                                    "canonicalEconomicRows",
                                    0,
                                    "nativeTrades",
                                    trades.size())));
        } finally {
            assertTrue(database.matches("native_backtest_http_[a-f0-9]{32}"));
            control.queryForList(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=? AND pid<>pg_backend_pid()",
                    database);
            control.execute("DROP DATABASE \"" + database + "\"");
        }
    }

    /** 旧费用模型也由正式接口执行；GUI的缺失原币状态必须来自真实旧合同，不能写假成交填充页面。 */
    private JsonNode legacyForGui(HttpClient http, int port, String token, JsonNode identity)
            throws Exception {
        var spec =
                mapper.createObjectNode()
                        .put("costSource", "EXPERIMENT_ASSUMPTION")
                        .put("feeRate", "0")
                        .put("slippageBps", "0")
                        .put("quantityStep", "0.0001")
                        .put("priceTick", "0.01")
                        .put("minimumQuantity", "0.0001")
                        .put("minimumNotional", "5")
                        .put("feeAssumptionVersion", "SYNTHETIC_EXPLICIT_ZERO")
                        .put("slippageAssumptionVersion", "SYNTHETIC_EXPLICIT_ZERO");
        String body =
                mapper.writeValueAsString(
                        Map.of(
                                "researchConfigId",
                                identity.path("research").asText(),
                                "name",
                                "Synthetic legacy scalar result",
                                "description",
                                "isolated legacy model UI proof",
                                "startTime",
                                "2026-09-25T00:00:00Z",
                                "endTime",
                                "2026-09-25T00:07:59Z",
                                "initialCapital",
                                100,
                                "executionSpec",
                                spec.toString(),
                                "evaluationSpec",
                                "{}"));
        var config = ok(send(http, port, "POST", "/api/backtest-configs", token, body));
        String configPath = "/api/backtest-configs/" + config.path("backtestConfigId").asText();
        ok(
                send(
                        http,
                        port,
                        "PATCH",
                        configPath + "/dataset",
                        token,
                        mapper.writeValueAsString(
                                Map.of("datasetId", identity.path("dataset").asText()))));
        ok(
                send(
                        http,
                        port,
                        "PATCH",
                        configPath + "/strategy-version",
                        token,
                        mapper.writeValueAsString(
                                Map.of(
                                        "strategyVersionId",
                                        identity.path("strategyVersion").asText()))));
        var run =
                ok(
                        send(
                                http,
                                port,
                                "POST",
                                "/api/backtest-runs",
                                token,
                                mapper.writeValueAsString(
                                        Map.of(
                                                "backtestConfigId",
                                                config.path("backtestConfigId").asText()))));
        run = ok(send(http, port, "POST", runPath(run) + "/start", token, null));
        assertEquals("SUCCEEDED", run.path("status").asText());
        assertEquals(
                "SUCCEEDED",
                ok(send(http, port, "POST", runPath(run) + "/evaluate", token, null))
                        .path("evaluationStatus")
                        .asText());
        var trades = ok(send(http, port, "GET", runPath(run) + "/sim-trades", token, null));
        assertTrue(trades.size() > 0);
        for (var trade : trades)
            for (String field :
                    List.of(
                            "feeCurrency",
                            "feeNativeAmount",
                            "feePaymentStatus",
                            "feeValuationScale")) assertTrue(trade.path(field).isNull(), field);
        return run;
    }

    /** 持久化事实与原币守恒核对；研究模拟事实不代替canonical订单、成交和账本。 */
    private void economics(
            JdbcTemplate jdbc, JsonNode run, JsonNode trades, String signed, int completedRuns)
            throws Exception {
        assertTrue(trades.size() > 0);
        boolean bought = false, sold = false;
        BigDecimal cash = new BigDecimal("100"),
                base = BigDecimal.ZERO,
                fee = BigDecimal.ZERO,
                rate = new BigDecimal(signed);
        for (JsonNode trade : trades) {
            boolean buy = "BUY".equals(trade.path("side").asText());
            bought |= buy;
            sold |= !buy;
            BigDecimal quantity = number(trade, "quantity"), price = number(trade, "tradePrice");
            BigDecimal nativeFee = number(trade, "feeNativeAmount"),
                    quoteFee = number(trade, "feeAmount"),
                    notional = quantity.multiply(price);
            assertEquals(buy ? "BTC" : "USDT", trade.path("feeCurrency").asText());
            assertEquals("RECEIVED_ASSET", trade.path("feePaymentStatus").asText());
            assertEquals(8, trade.path("feeValuationScale").asInt());
            equal(
                    (buy ? quantity : notional).multiply(rate).setScale(8, RoundingMode.HALF_UP),
                    nativeFee);
            equal(
                    (buy ? nativeFee.multiply(price) : nativeFee).setScale(8, RoundingMode.HALF_UP),
                    quoteFee);
            base = buy ? base.add(quantity).subtract(nativeFee) : base.subtract(quantity);
            cash = buy ? cash.subtract(notional) : cash.add(notional).subtract(nativeFee);
            fee = fee.add(quoteFee);
        }
        assertTrue(bought && sold);
        JsonNode summary = mapper.readTree(run.path("summaryJson").asText());
        equal(cash, number(summary, "finalCashBalance"));
        equal(base, number(summary, "finalPositionQuantity"));
        equal(fee, number(summary, "totalFee"));
        assertTrue(summary.path("researchFacts").path("assumptions").path("feeRate").isNull());
        assertEquals(
                "BTC",
                summary.path("researchFacts").path("benchmark").path("feeCurrency").asText());
        assertEquals(
                trades.size(),
                jdbc.queryForObject(
                        "SELECT count(*) FROM sim_trades WHERE backtest_run_id=? AND fee_currency IS NOT NULL",
                        Integer.class,
                        run.path("backtestRunId").asText()));
        assertEquals(
                completedRuns,
                jdbc.queryForObject(
                        "SELECT count(*) FROM backtest_runs WHERE status='SUCCEEDED'",
                        Integer.class));
        for (String table : List.of("orders", "trades", "ledger_entries"))
            assertEquals(
                    0L, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class), table);
        assertEquals(
                "ENGAGED",
                jdbc.queryForObject(
                        "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                        String.class));
    }

    private static BigDecimal number(JsonNode node, String key) {
        assertTrue(node.hasNonNull(key), key);
        return new BigDecimal(node.path(key).asText());
    }

    private static void equal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    private static String runPath(JsonNode run) {
        return "/api/backtest-runs/" + run.path("backtestRunId").asText();
    }

    private static void assertDenied(HttpResponse<String> response) {
        assertTrue(
                List.of(400, 404).contains(response.statusCode()),
                "cross owner status=" + response.statusCode());
    }

    private String login(HttpClient http, int port, String username) throws Exception {
        var body =
                mapper.writeValueAsString(
                        Map.of(
                                "username",
                                username,
                                "password",
                                NativeFeeBacktestHttpFixtureMain.PASSWORD));
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
                        + NativeFeeBacktestHttpFixtureMain.class.getName()
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
        builder.environment().put("TASK_NATIVE_BACKTEST_DB_URL", url);
        builder.environment().put("TASK_NATIVE_BACKTEST_DB_USER", user);
        builder.environment().put("TASK_NATIVE_BACKTEST_DB_PASSWORD", password);
        builder.environment()
                .put(
                        "TASK_NATIVE_BACKTEST_UI",
                        Boolean.toString(Boolean.getBoolean("nq.native.backtest.ui")));
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
